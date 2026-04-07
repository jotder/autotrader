package com.rj.broker;

import com.rj.model.ApiResponse;
import com.rj.model.MultiOrderResult;
import com.rj.model.OrderEntry;
import com.rj.model.OrderResult;
import com.rj.model.PositionsSummary;
import com.tts.in.model.MultiLegModel;
import com.tts.in.model.PlaceOrderModel;
import com.tts.in.model.PositionConversionModel;

import java.util.List;

public interface IOrderAdapter {
    OrderResult placeOrder(PlaceOrderModel model);
    MultiOrderResult placeMultipleOrders(List<PlaceOrderModel> models);
    MultiOrderResult placeMultiLegOrder(List<MultiLegModel> models);
    OrderResult modifyOrder(PlaceOrderModel model);
    MultiOrderResult modifyMultipleOrders(List<PlaceOrderModel> models);
    OrderResult cancelOrder(String orderId);
    MultiOrderResult cancelMultipleOrders(List<String> orderIds);
    PositionsSummary getPositions();
    ApiResponse exitPositions(List<String> positionIds);
    ApiResponse exitPositionBySegmentSidePrdType(int[] sides, int[] segments, String[] products);
    ApiResponse convertPosition(PositionConversionModel model);
    List<OrderEntry> getOrders();
    List<OrderEntry> getOrderById(String orderId);
}
