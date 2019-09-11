package com.buffrapp;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;

public class ProductsAdapter extends RecyclerView.Adapter<ProductsAdapter.ViewHolder> {

    private static final String TAG = "ProductsAdapter";

    private ArrayList<ArrayList<String>> mData;
    private LayoutInflater mInflater;
    private ItemClickListener mClickListener;

    // Put data into the constructor method.
    ProductsAdapter(Context context, ArrayList<ArrayList<String>> data) {
        mInflater = LayoutInflater.from(context);
        mData = data;
    }

    void setNewData(ArrayList<ArrayList<String>> data) {
        Log.d(TAG, "setNewData: " + data.toString());
        mData = data;
        notifyDataSetChanged();
    }

    // Inflate layout.
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = mInflater.inflate(R.layout.recyclerview_item, parent, false);
        return new ViewHolder(view);
    }

    // Bind TV data for each row.
    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        holder.productNameTextView.setText(mData.get(position).get(0));
        holder.productPriceTextView.setText(mData.get(position).get(1));
    }

    @Override
    public int getItemCount() {
        int size = 0;
        if (mData != null) {
            size = mData.size();
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

        ViewHolder(View itemView) {
            super(itemView);
            productNameTextView = itemView.findViewById(R.id.tvProductName);
            productPriceTextView = itemView.findViewById(R.id.tvProductPrice);
            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(View view) {
            if (mClickListener != null) mClickListener.onItemClick(view, getAdapterPosition());
        }
    }
}