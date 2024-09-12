package com.wingtech.cloudserver.client.adapter;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.wingtech.cloudserver.client.R;

import java.util.List;
import java.util.Map;

/**
 * Created by lijiwei on 2017/10/18.
 */

public class AppListAdapter extends BaseAdapter {

    private List<Map<String, Object>> data;
    private LayoutInflater mInflater;

    public AppListAdapter(Context context, List<Map<String, Object>> data) {
        this.data = data;
        this.mInflater = LayoutInflater.from(context);
    }

    @Override
    public int getCount() {
        return data.size();
    }

    @Override
    public Object getItem(int position) {
        return data.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder = null;
        if (convertView == null) {
            holder = new ViewHolder();
            convertView = mInflater.inflate(R.layout.list_item, null);
            holder.appIcon = (ImageView) convertView.findViewById(R.id.img);
            holder.appName = (TextView) convertView.findViewById(R.id.name);
            holder.pkgName = (TextView) convertView.findViewById(R.id.pkgName);
            holder.className = (TextView) convertView.findViewById(R.id.className);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }
        holder.appIcon.setImageDrawable((Drawable) data.get(position).get("appIcon"));
        holder.appName.setText(data.get(position).get("appName").toString());
        holder.pkgName.setText(data.get(position).get("pkgName").toString());
        holder.className.setText(data.get(position).get("className").toString());
        return convertView;
    }

    class ViewHolder {
        ImageView appIcon;
        TextView appName;
        TextView pkgName;
        TextView className;
    }
}
