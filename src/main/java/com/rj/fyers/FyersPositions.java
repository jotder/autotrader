package com.rj.fyers;

import com.rj.model.ApiResponse;
import com.rj.model.PositionsSummary;
import com.tts.in.model.FyersClass;
import com.tts.in.model.PositionConversionModel;
import com.tts.in.utilities.Tuple;
import org.json.JSONObject;

import java.util.List;

public class FyersPositions {
    FyersClass fyersClass;

    public FyersPositions() {
        fyersClass = FyersClientFactory.getConfiguredInstance();
    }

    public PositionsSummary getPositions() {
        Tuple<JSONObject, JSONObject> tuple = fyersClass.GetPositions();
        if (tuple.Item2() != null) {
            System.out.println("Positions Error: " + tuple.Item2());
            return null;
        }
        return PositionsSummary.from(tuple.Item1());
    }

    /** Pass an empty list to exit all open positions. */
    public ApiResponse exitPositions(List<String> positionIds) {
        Tuple<JSONObject, JSONObject> tuple = fyersClass.ExitPositions(positionIds);
        if (tuple.Item2() != null) {
            System.out.println("ExitPositions Error: " + tuple.Item2());
            return null;
        }
        return ApiResponse.from(tuple.Item1());
    }

    public ApiResponse exitPositionBySegmentSidePrdType(int[] sides, int[] segments, String[] products) {
        Tuple<JSONObject, JSONObject> tuple = fyersClass.ExitPositionBySegmentSidePrdType(sides, segments, products);
        if (tuple.Item2() != null) {
            System.out.println("ExitPositionByFilter Error: " + tuple.Item2());
            return null;
        }
        return ApiResponse.from(tuple.Item1());
    }

    public ApiResponse convertPosition(PositionConversionModel model) {
        Tuple<JSONObject, JSONObject> tuple = fyersClass.PositionConversion(model);
        if (tuple.Item2() != null) {
            System.out.println("PositionConversion Error: " + tuple.Item2());
            return null;
        }
        return ApiResponse.from(tuple.Item1());
    }
}
