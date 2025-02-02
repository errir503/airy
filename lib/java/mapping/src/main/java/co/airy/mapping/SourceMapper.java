package co.airy.mapping;

import co.airy.mapping.model.Content;

import java.util.List;

public interface SourceMapper {
    List<String> getIdentifiers();

    List<Content> render(String payload) throws Exception;
}
