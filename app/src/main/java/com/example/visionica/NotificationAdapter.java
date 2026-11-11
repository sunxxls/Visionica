package com.example.visionica;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NotificationAdapter
        extends RecyclerView.Adapter<NotificationAdapter.ViewHolder> {

    private final Context                context;
    private final List<NotificationModel> items;
    private final FirebaseFirestore       db;

    public NotificationAdapter(Context context, List<NotificationModel> items) {
        this.context = context;
        this.items   = items;
        this.db      = FirebaseFirestore.getInstance();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context)
                .inflate(R.layout.item_notification, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        NotificationModel n = items.get(position);

        h.tvTitle.setText(n.getTitle());
        h.tvMessage.setText(n.getMessage());

        // Fecha formateada
        if (n.getTimestamp() > 0) {
            SimpleDateFormat sdf =
                    new SimpleDateFormat("dd/MM/yyyy  HH:mm", Locale.getDefault());
            h.tvTime.setText(sdf.format(new Date(n.getTimestamp())));
        }

        // ── Indicador de no leído ───────────────────────────────────
        h.viewUnread.setVisibility(n.isRead() ? View.GONE : View.VISIBLE);

        // Fondo de la card: ligeramente diferente si no fue leída
        if (!n.isRead()) {
            h.card.setCardBackgroundColor(
                    ContextCompat.getColor(context, R.color.notif_unread_bg));
        } else {
            h.card.setCardBackgroundColor(
                    ContextCompat.getColor(context, android.R.color.white));
        }

        // ── Ícono según tipo ────────────────────────────────────────
        if ("attendance".equals(n.getType())) {
            h.ivIcon.setImageResource(R.drawable.ic_face);
            h.ivIcon.setColorFilter(
                    ContextCompat.getColor(context, R.color.primary_purple));
        } else if ("calendar".equals(n.getType())) {
            h.ivIcon.setImageResource(R.drawable.ic_schedule);
            h.ivIcon.setColorFilter(
                    ContextCompat.getColor(context, R.color.color_calendar));
        } else {
            h.ivIcon.setImageResource(R.drawable.ic_notifications);
            h.ivIcon.setColorFilter(
                    ContextCompat.getColor(context, R.color.primary_purple));
        }

        // ── Marcar como leída al pulsar ─────────────────────────────
        h.itemView.setOnClickListener(v -> {
            if (!n.isRead() && n.getId() != null) {
                db.collection("notifications")
                        .document(n.getId())
                        .update("read", true)
                        .addOnSuccessListener(unused -> {
                            n.setRead(true);
                            notifyItemChanged(h.getAdapterPosition());
                        });
            }
        });
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        CardView  card;
        ImageView ivIcon;
        TextView  tvTitle, tvMessage, tvTime;
        View      viewUnread;

        ViewHolder(@NonNull View v) {
            super(v);
            card       = v.findViewById(R.id.cardNotification);
            ivIcon     = v.findViewById(R.id.ivNotifIcon);
            tvTitle    = v.findViewById(R.id.tvNotifTitle);
            tvMessage  = v.findViewById(R.id.tvNotifMessage);
            tvTime     = v.findViewById(R.id.tvNotifTime);
            viewUnread = v.findViewById(R.id.viewUnread);
        }
    }
}