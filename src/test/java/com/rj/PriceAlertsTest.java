import com.rj.model.PriceAlertBook;
import com.tts.in.model.PriceAlertModel;
import fyers.FyersPriceAlerts;

void main() {
    FyersPriceAlerts app = new FyersPriceAlerts();

    // 1. Get all price alerts
    PriceAlertBook alerts = app.getPriceAlerts();
    System.out.println("Price Alerts: " + alerts);

    // 2. Create a price alert
    PriceAlertModel create = new PriceAlertModel();
    create.Agent = "fyers-api";
    create.AlertType = 1;
    create.Name = "gold alert";
    create.Symbol = "NSE:GOLDBEES-EQ";
    create.ComparisonType = "LTP";
    create.Condition = "GT";
    create.Value = 9999;
    create.Notes = "Gold Alert";
    System.out.println("Created: " + app.createPriceAlert(create));

    // 3. Modify (update) an alert — symbol cannot be changed
    PriceAlertModel update = new PriceAlertModel();
    update.AlertId = "5397131";
    update.Agent = "fyers-api";
    update.AlertType = 1;
    update.Name = "NSE:SILVERMIC25DECFUT";
    update.Symbol = "NSE:SILVERMIC25DECFUT";
    update.ComparisonType = "LTP";
    update.Condition = "LTE";
    update.Value = 50;
    System.out.println("Updated: " + app.updatePriceAlert(update));

    // 4. Enable / Disable toggle
    System.out.println("Toggled: " + app.toggleAlert("6249070"));

    // 5. Delete an alert
    System.out.println("Deleted: " + app.deletePriceAlert("539713"));
}
