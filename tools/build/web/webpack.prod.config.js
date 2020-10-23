const path = require("path");
const webpack = require("webpack");
const HtmlWebpackPlugin = require("html-webpack-plugin");
const CopyWebpackPlugin = require("copy-webpack-plugin");
const BundleAnalyzerPlugin = require("webpack-bundle-analyzer")
  .BundleAnalyzerPlugin;
const TerserPlugin = require("terser-webpack-plugin");
const OptimizeCSSAssetsPlugin = require("optimize-css-assets-webpack-plugin");

function resolveTsconfigPathsToAlias({ tsconfigPath, basePath }) {
  const { paths } = require(tsconfigPath).compilerOptions;
  const stripGlobs = path => path.replace("/*", "");

  return Object.keys(paths).reduce((aliases, moduleMappingKey) => {
    const key = stripGlobs(moduleMappingKey);
    const value = path.resolve(
      basePath,
      stripGlobs(paths[moduleMappingKey][1]).replace("*", "")
    );

    return {
      ...aliases,
      [key]: value
    };
  }, {});
}

module.exports = (env, argv) => ({
  mode: "production",
  target: "web",
  bail: true, // stop compilation on first error
  resolve: {
    alias: resolveTsconfigPathsToAlias({
      tsconfigPath: path.resolve(argv.tsconfig),
      basePath: process.cwd()
    })
  },
  output: {
    path: path.resolve(argv.path),
    publicPath: "/",
    filename: "js/[name].[chunkhash:8].js"
  },

  optimization: {
    minimize: true,
    minimizer: [
      new TerserPlugin({
        sourceMap: true
      })
    ]
  },

  devtool: "source-map",

  module: {
    rules: [
      {
        test: /\.(mjs|js)$/,
        exclude: /node_modules/,
        loader: "babel-loader",
        options: {
          cacheDirectory: false,
          presets: [
            [
              "@babel/preset-env",
              {
                useBuiltIns: "entry",
                corejs: 3,
                modules: false,
                targets: [">0.2%", "not dead", "not op_mini all"]
              }
            ]
          ]
        }
      },
      {
        test: /\.(mjs|js)$/,
        exclude: /node_modules/,
        loader: "babel-loader",
        options: {
          cacheDirectory: true,
          presets: [
            [
              "@babel/preset-env",
              {
                useBuiltIns: "entry",
                corejs: 3,
                modules: false,
                targets: [">0.2%", "not dead", "not op_mini all"]
              }
            ]
          ]
        }
      },
      {
        test: /\.module\.scss$/,
        use: [
          "style-loader",
          {
            loader: "css-loader",
            options: {
              importLoaders: 1,
              modules: {
                localIdentName: "[name]__[local]--[hash:base64:5]"
              }
            }
          },
          "sass-loader"
        ]
      },
      {
        test: /(?<!\.module)\.(scss|css)$/,
        use: [
          "style-loader",
          {
            loader: "css-loader",
            options: {
              importLoaders: 1
            }
          },
          "sass-loader"
        ]
      },
      {
        test: /\.(ico|jpg|jpeg|png|gif|eot|otf|webp|ttf|woff|woff2|svg)(\?.*)?$/,
        loader: "file-loader",
        options: {
          name: "media/[name].[hash:8].[ext]"
        }
      },
      {
        test: /\.js$/,
        use: ["source-map-loader"],
        enforce: "pre"
      }
    ]
  },
  plugins: [
    // Reduce the moment bundle file by only loading de and en (https://github.com/jmblog/how-to-optimize-momentjs-with-webpack)
    new webpack.ContextReplacementPlugin(/moment[/\\]locale$/, /en|de/),

    new webpack.DefinePlugin({
      "process.env.NODE_ENV": "'production'"
    }),
    new CopyWebpackPlugin([
      {
        from: "**/public/**/*",
        ignore: ["**/node_modules/**"],
        transformPath(targetPath) {
          const splits = targetPath.split("public/");
          return splits[1];
        }
      }
    ]),
    new HtmlWebpackPlugin({
      template: "!!ejs-compiled-loader!" + path.resolve(argv.index),
      inject: true,
      filename: "index.html",
      minify: { removeComments: true, collapseWhitespace: true }
    }),
    new BundleAnalyzerPlugin({
      analyzerMode: "static",
      openAnalyzer: false
    }),
    new OptimizeCSSAssetsPlugin()
  ]
});