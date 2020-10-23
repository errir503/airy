load("@rules_java//java:defs.bzl", "java_binary")
load("@io_bazel_rules_docker//container:container.bzl", "container_image")
# Spring Boot Executable JAR Layout specification
# reverse engineered from the Spring Boot maven plugin
# /
# /META-INF/
# /META-INF/MANIFEST.MF                        <-- very specific manifest for Spring Boot (generated by this rule)
# /BOOT-INF
# /BOOT-INF/classes
# /BOOT-INF/classes/**/*.class                 <-- compiled application classes, must include @SpringBootApplication class
# /BOOT-INF/classes/META-INF/*                 <-- application level META-INF config files (e.g. spring.factories)
# /BOOT-INF/lib
# /BOOT-INF/lib/*.jar                          <-- all upstream transitive dependency jars must be here (except spring-boot-loader)
# /org/springframework/boot/loader
# /org/springframework/boot/loader/**/*.class  <-- the Spring Boot Loader classes must be here

# ***************************************************************
# Dependency Aggregator Rule
# do not use directly, see the SpringBoot Macro below
def _depaggregator_rule_impl(ctx):
    # magical incantation for getting upstream transitive closure of java deps
    merged = java_common.merge([dep[java_common.provider] for dep in ctx.attr.deps])
    jars = merged.transitive_runtime_jars

    return [DefaultInfo(files = jars)]

_depaggregator_rule = rule(
    implementation = _depaggregator_rule_impl,
    attrs = {
        "depaggregator_rule": attr.label(),
        "deps": attr.label_list(providers = [java_common.provider]),
    },
)

# ***************************************************************
# SpringBoot Rule
#  do not use directly, see the SpringBoot Macro below
def _springboot_rule_impl(ctx):
    outs = depset(transitive = [ctx.attr.app_compile_rule.files, ctx.attr.genmanifest_rule.files, ctx.attr.genjar_rule.files])

    merged = java_common.merge([dep[java_common.provider] for dep in ctx.attr.deps])
    jars = merged.transitive_runtime_jars

    return [DefaultInfo(files = outs)]

_springboot_rule = rule(
    implementation = _springboot_rule_impl,
    attrs = {
        "app_compile_rule": attr.label(),
        "dep_aggregator_rule": attr.label(),
        "genmanifest_rule": attr.label(),
        "genjar_rule": attr.label(),
        "deps": attr.label_list(providers = [java_common.provider]),
    },
)

# ***************************************************************
# SpringBoot Macro
#  invoke this from your BUILD file
#
#  name:    name of your application
#  main_class:  the classname (java package+type) of the @SpringBootApplication class in your app
#  deps:  the array of upstream dependencies
#  resources (optional): list of resources to build into the jar, if not specified, assumes src/main/resources/**/*
def springboot(name, main_class, deps, srcs, resources = []):
    # Create the subrule names
    appcompile_rule = "app"
    dep_aggregator_rule = "deps"
    genmanifest_rule = "genmanifest"
    genjar_rule = "genjar"

    # Append to the passed deps with the standard Spring Boot deps as a convenience
    _add_default_deps(deps)

    # for resources, assume all files in the standard location unless overridden by config
    if len(resources) == 0:
        resources = native.glob(["src/main/resources/**/*"])

    found = False
    merged_log4j = "src/main/resources/log4j2.properties"

    for idx, resource in enumerate(resources):
        if resource.rfind("src/main/resources/log4j-custom.properties") != -1:
            native.genrule(
                name = "merged_log4j_target",
                cmd = "cat $(location src/main/resources/log4j-custom.properties) $(location //tools/build:log4j2.properties) >> $@",
                outs = [merged_log4j],
                srcs = [resource, "//tools/build:log4j2.properties"],
            )

            resources[idx] = merged_log4j
            found = True

    if found == False:
        native.genrule(
            name = "merged_log4j_target",
            cmd = "cp $< $@",
            outs = [merged_log4j],
            srcs = ["//tools/build:log4j2.properties"],
        )

        resources.append(merged_log4j)

    # SUBRULE 1: COMPILE THE SPRING BOOT APPLICATION
    java_binary(
        name = appcompile_rule,
        srcs = srcs,
        resources = resources,
        main_class = main_class,
        # Add parameters to enable this jdbi feature http://jdbi.org/#_compiling_with_parameter_names
        javacopts = ["-parameters"],
        deps = deps,
    )

    # SUBRULE 2: AGGREGATE UPSTREAM DEPS
    #  Aggregate transitive closure of upstream Java deps
    _depaggregator_rule(
        name = dep_aggregator_rule,
        deps = deps,
    )

    # SUBRULE 3: GENERATE THE MANIFEST
    #  NICER: derive the Build JDK and Boot Version values by scanning transitive deps
    genmanifest_cmd = "echo 'Manifest-Version: 1.0' >$@;" + \
                      "echo 'Created-By: Bazel' >>$@;" + \
                      "echo 'Built-By: Bazel' >>$@;" + \
                      "echo 'Throw-Down: hootenanny' >>$@;" + \
                      "echo 'Main-Class: org.springframework.boot.loader.JarLauncher' >>$@;" + \
                      "echo 'Spring-Boot-Classes: BOOT-INF/classes/' >>$@;" + \
                      "echo 'Spring-Boot-Lib: BOOT-INF/lib/' >>$@;" + \
                      "echo 'Start-Class: " + main_class + "' >>$@;"
    genmanifest_out = "MANIFEST.MF"
    native.genrule(
        name = genmanifest_rule,
        cmd = genmanifest_cmd,
        message = "SpringBoot rule is writing the MANIFEST.MF...",
        outs = [genmanifest_out],
    )

    # SUBRULE 4: INVOKE THE BASH SCRIPT THAT DOES THE PACKAGING
    # The resolved input_file_paths array is made available as the $(SRCS) token in the cmd string.
    # Starlark will convert the logical input_file_paths into real file system paths when surfaced in $(SRCS)
    #  cmd format (see springboot_pkg.sh)
    #    param1: boot application classname (the @SpringBootApplication class)
    #    param2: executable jar output filename to write to
    #    param3: compiled application jar
    #    param4: manifest file
    #    paramN: upstream transitive dependency jar(s)
    native.genrule(
        name = genjar_rule,
        srcs = [":" + appcompile_rule, ":" + genmanifest_rule, ":" + dep_aggregator_rule],
        cmd = "$(location //tools/build:springboot_pkg.sh) " + main_class + " $@ $(SRCS)",
        tools = ["//tools/build:springboot_pkg.sh"],
        outs = ["app_springboot.jar"],
    )

    container_image(
        name = "image",
        base = "//:base_image",
        files = [":genjar"],
        cmd = [
            "java",
            "-XshowSettings:vm",
            "-XX:MaxRAMPercentage=70",
            "-XX:-UseCompressedOops",
            "-jar",
            "app_springboot.jar",
            "-Dsun.net.inetaddr.ttl=0",
        ],
    )

    # MASTER RULE: Create the composite rule that will aggregate the outputs of the subrules
    _springboot_rule(
        name = name,
        app_compile_rule = ":" + appcompile_rule,
        dep_aggregator_rule = ":" + dep_aggregator_rule,
        genmanifest_rule = ":" + genmanifest_rule,
        genjar_rule = ":" + genjar_rule,
        deps = deps,
    )

# end springboot macro

# Default Dependencies
# Add in the standard Spring Boot dependencies so that app devs don't
# need to explicitly state them every time in the BUILD file.
def _add_default_deps(deps):
    _safe_add(deps, "//backend/lib/log:log")
    _safe_add(deps, "@maven//:javax_servlet_javax_servlet_api")
    _safe_add(deps, "@maven//:javax_validation_validation_api")

    _safe_add(deps, "@maven//:org_springframework_spring_core")
    _safe_add(deps, "@maven//:org_springframework_spring_web")
    _safe_add(deps, "@maven//:org_springframework_spring_beans")
    _safe_add(deps, "@maven//:org_springframework_spring_context")
    _safe_add(deps, "@maven//:org_springframework_spring_webmvc")

    _safe_add(deps, "@maven//:org_springframework_boot_spring_boot")
    _safe_add(deps, "@maven//:org_springframework_boot_spring_boot_autoconfigure")
    _safe_add(deps, "@maven//:org_springframework_boot_spring_boot_actuator_autoconfigure")
    _safe_add(deps, "@maven//:org_springframework_boot_spring_boot_loader")
    _safe_add(deps, "@maven//:org_springframework_boot_spring_boot_starter")
    _safe_add(deps, "@maven//:org_springframework_boot_spring_boot_starter_jetty")
    _safe_add(deps, "@maven//:org_springframework_boot_spring_boot_starter_web")
    _safe_add(deps, "@maven//:org_springframework_boot_spring_boot_starter_actuator")
    _safe_add(deps, "@maven//:org_springframework_boot_spring_boot_actuator")

    _safe_add(deps, "//:jackson")
    _safe_add(deps, "//:lombok")

# Bazel will fail if a dependency appears twice for the same target, so be
# safe when adding a dependencies to the deps list
def _safe_add(deps, dep):
    if not (dep in deps):
        deps.append(dep)