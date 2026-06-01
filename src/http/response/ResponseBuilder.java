public class ResponseBuilder implements RespondingBuilder<ResponseBuilder> {
    private final Response response;
    
    public ResponseBuilder() {
        this.response = new Response();
    }

    @Override
    public ResponseBuilder setVersion(String version) {
        response.setVersion(version);
        return this;
    }

    @Override
    public ResponseBuilder setStatus(int status) {
        response.setStatus(status);
        return this;
    }

    @Override
    public ResponseBuilder setHeader(String key, String value) {
        response.setHeader(key, value);
        return this;
    }

    @Override
    public ResponseBuilder addCookie(Cookie cookie) {
        response.addCookie(cookie);
        return this;
    }

    @Override
    public ResponseBuilder setBody(Object body) {
        response.setBody(body);
        return this;
    }

    @Override
    public Response build() {
        return this.response;
    }
}