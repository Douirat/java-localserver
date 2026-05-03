package exceptions;

public class IllegalRouteException extends RuntimeException {
    public IllegalRouteException(String message){
        super(message);
    }
}