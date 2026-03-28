import com.rj.model.ClientProfile;
import com.rj.fyers.FyersProfile;

void main() {

    FyersProfile fyersProfile = new FyersProfile();
    ClientProfile profile = fyersProfile.getProfile();
    System.out.println(profile);
}

