
import fyers.FyersProfile;
import com.rj.model.ClientProfile;

void main(String[] args) {

    FyersProfile fyersProfile = new FyersProfile();
    ClientProfile profile = fyersProfile.getProfile();
    System.out.println(profile);
}

