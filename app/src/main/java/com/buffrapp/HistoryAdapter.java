package com.buffrapp;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

    private static final String TAG = "HistoryAdapter";

    private JSONArray mData;
    private LayoutInflater mInflater;
    private ItemClickListener mClickListener;

    // Put data into the constructor method.
    HistoryAdapter(Context context, JSONArray data) {
        mInflater = LayoutInflater.from(context);
        mData = data;
    }

    void setNewData(JSONArray data) {
        Log.d(TAG, "setNewData: " + data.toString());
        mData = data;
        notifyDataSetChanged();
    }

    // Inflate layout.
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = mInflater.inflate(R.layout.recyclerview_history_item, parent, false);
        return new ViewHolder(view);
    }

    // Bind TV data for each row.
    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        try {
            JSONObject jsonObject = mData.getJSONObject(position);
            Log.d(TAG, "onBindViewHolder: jsonObject: " + jsonObject.toString());
            holder.productNameTextView.setText(jsonObject.getString("Producto_Nombre"));
            holder.productPriceTextView.setText(String.format(holder.itemView.getContext().getString(R.string.product_price), jsonObject.getString("Producto_Precio")));
            holder.orderHolderTextView.setText(String.format(holder.itemView.getContext().getString(R.string.order_holder), jsonObject.getString("Nombre_Administrador")));
            holder.orderReceivedTextView.setText(jsonObject.getString("FH_Recibido"));
            if (jsonObject.isNull("DNI_Cancelado")) {
                holder.orderTakenTextView.setText(String.format(holder.itemView.getContext().getString(R.string.order_taken), jsonObject.getString("FH_Tomado")));
                holder.orderReadyTextView.setText(String.format(holder.itemView.getContext().getString(R.string.order_ready), jsonObject.getString("FH_Listo")));
                holder.orderDeliveredTextView.setText(String.format(holder.itemView.getContext().getString(R.string.order_delivered), jsonObject.getString("FH_Entregado")));
            } else {
                holder.orderTakenTextView.setVisibility(View.GONE);
                holder.orderReadyTextView.setVisibility(View.GONE);
                holder.orderDeliveredTextView.setVisibility(View.GONE);
                holder.orderCancelledTextView.setVisibility(View.VISIBLE);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public int getItemCount() {
        int size = 0;
        if (mData != null) {
            size = mData.length();
        }

        return size;
    }

    void setClickListener(ItemClickListener itemClickListener) {
        this.mClickListener = itemClickListener;
    }

    public interface ItemClickListener {
        void onItemClick(View view, int position);
    }

    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        private TextView productNameTextView;
        private TextView productPriceTextView;
        private TextView orderHolderTextView;
        private TextView orderReceivedTextView;
        private TextView orderTakenTextView;
        private TextView orderReadyTextView;
        private TextView orderDeliveredTextView;
        private TextView orderCancelledTextView;

        ViewHolder(View itemView) {
            super(itemView);

            productNameTextView = itemView.findViewById(R.id.tvProductName);
            productPriceTextView = itemView.findViewById(R.id.tvProductPrice);
            orderHolderTextView = itemView.findViewById(R.id.tvOrderHolder);
            orderReceivedTextView = itemView.findViewById(R.id.tvOrderReceived);
            orderTakenTextView = itemView.findViewById(R.id.tvOrderTaken);
            orderReadyTextView = itemView.findViewById(R.id.tvOrderReady);
            orderDeliveredTextView = itemView.findViewById(R.id.tvOrderDelivered);
            orderCancelledTextView = itemView.findViewById(R.id.tvOrderCanceled);

            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(View view) {
            if (mClickListener != null) mClickListener.onItemClick(view, getAdapterPosition());
        }
    }
}