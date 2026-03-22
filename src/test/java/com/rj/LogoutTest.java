
import fyers.FyersUser;
import org.json.JSONObject;

void main() {
    FyersUser app = new FyersUser();
    JSONObject result = app.logout();
    System.out.println("Logout: " + result.toString(6));
}