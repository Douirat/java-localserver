package http.connecting.state;


    public enum RequestState {
        REQUEST_LINE,
        HEADERS,
        BODY,
        COMPLETE
    }