
import fyers.FyersPositions;
import com.rj.model.ApiResponse;
import com.rj.model.PositionsSummary;
import com.tts.in.model.PositionConversionModel;
import com.tts.in.utilities.ProductType;

import java.util.ArrayList;
import java.util.List;

void main() {
    FyersPositions app = new FyersPositions();

    // 1. Get all open/closed positions for today
    PositionsSummary positions = app.getPositions();
    System.out.println("Positions: " + positions);

    // 2. Exit all open positions (empty list = exit all)
    ApiResponse exitAll = app.exitPositions(new ArrayList<>());
     System.out.println("Exit All Positions: " + exitAll);

    // 3. Exit specific positions by id
    ApiResponse exitById = app.exitPositions(List.of("NSE:IDEA-EQ-INTRADAY"));
     System.out.println("Exit By Id: " + exitById);

    // 4. Exit by segment, side, and product type
    ApiResponse exitByFilter = app.exitPositionBySegmentSidePrdType(
            new int[]{1}, new int[]{10}, new String[]{ProductType.INTRADAY});
     System.out.println("Exit By Filter: " + exitByFilter);

    // 5. Convert position from INTRADAY to CNC
     PositionConversionModel model = new PositionConversionModel();
     model.Symbol      = "NSE:IDEA-EQ-INTRADAY";
     model.Side        = 1;
     model.ConvertQty  = 1;
     model.ConvertFrom = "INTRADAY";
     model.ConvertTo   = "CNC";
     model.Overnight   = 0;
    ApiResponse convert = app.convertPosition(model);
     System.out.println("Convert Position: " + convert);
}
