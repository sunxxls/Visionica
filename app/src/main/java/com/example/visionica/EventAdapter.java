package com.example.visionica;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class EventAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    // ── Tipos de fila ──────────────────────────────────────────────────
    private static final int TYPE_SEPARATOR = 0;
    private static final int TYPE_EVENT     = 1;

    // ══════════════════════════════════════════════════════════════════
    // Clase base — todos los items de la lista heredan de aquí
    // ══════════════════════════════════════════════════════════════════
    public static abstract class ListItem {
        public abstract int getType();
    }

    // ══════════════════════════════════════════════════════════════════
    // Separador de fecha  ej: "Martes, 16 de mayo de 2026"
    // ══════════════════════════════════════════════════════════════════
    public static class SeparatorItem extends ListItem {
        public final String label;
        public SeparatorItem(String label) { this.label = label; }
        @Override public int getType() { return TYPE_SEPARATOR; }
    }

    // ══════════════════════════════════════════════════════════════════
    // EventItem — igual que el tuyo original + campo timestamp
    // ══════════════════════════════════════════════════════════════════
    public static class EventItem extends ListItem {
        public String docId;
        public String title;
        public String description;
        public String time;
        public String color;
        public String category;
        public long   timestamp;   // ← nuevo, necesario para los separadores

        // Constructor nuevo (con timestamp)
        public EventItem(String docId, String title, String description,
                         String time, String color, String category, long timestamp) {
            this.docId       = docId;
            this.title       = title;
            this.description = description;
            this.time        = time;
            this.color       = color;
            this.category    = category;
            this.timestamp   = timestamp;
        }

        // Constructor legacy (sin timestamp) — por si lo usas en otro lado
        public EventItem(String docId, String title, String description,
                         String time, String color, String category) {
            this(docId, title, description, time, color, category, 0L);
        }

        @Override public int getType() { return TYPE_EVENT; }
    }

    // ── Callback eliminar — igual que el tuyo original ─────────────────
    public interface OnDeleteListener {
        void onDelete(EventItem item, int position);
    }

    // ── Lista y listener ───────────────────────────────────────────────
    private final List<ListItem>   items;
    private final OnDeleteListener deleteListener;

    public EventAdapter(List<ListItem> items, OnDeleteListener deleteListener) {
        this.items          = items;
        this.deleteListener = deleteListener;
    }

    // ── getItemViewType ────────────────────────────────────────────────
    @Override
    public int getItemViewType(int position) {
        return items.get(position).getType();
    }

    // ── onCreateViewHolder ─────────────────────────────────────────────
    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());

        if (viewType == TYPE_SEPARATOR) {
            View v = inflater.inflate(R.layout.item_event_separator, parent, false);
            return new SeparatorViewHolder(v);
        } else {
            // Usa tu mismo item_event.xml original — no cambia nada de XML
            View v = inflater.inflate(R.layout.item_event, parent, false);
            return new EventViewHolder(v);
        }
    }

    // ── onBindViewHolder ───────────────────────────────────────────────
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ListItem listItem = items.get(position);

        if (holder instanceof SeparatorViewHolder) {
            // ── Separador de fecha ─────────────────────────────────────
            SeparatorItem sep = (SeparatorItem) listItem;
            ((SeparatorViewHolder) holder).tvDate.setText(sep.label);

        } else if (holder instanceof EventViewHolder) {
            // ── Fila de evento — lógica idéntica a tu original ─────────
            EventItem     item = (EventItem) listItem;
            EventViewHolder h  = (EventViewHolder) holder;

            // Título
            h.tvTitle.setText(item.title);

            // Descripción
            if (item.description != null && !item.description.isEmpty()) {
                h.tvDescription.setText(item.description);
                h.tvDescription.setVisibility(View.VISIBLE);
            } else {
                h.tvDescription.setVisibility(View.GONE);
            }

            // Hora
            h.tvTime.setText(item.time != null ? item.time : "");

            // Categoría
            h.tvCategory.setText(item.category != null ? item.category : "");

            // Color de barra lateral y texto categoría
            try {
                int color = Color.parseColor(item.color != null ? item.color : "#8B5394");
                h.statusBar.setBackgroundColor(color);
                h.tvCategory.setTextColor(color);
            } catch (Exception e) {
                h.statusBar.setBackgroundColor(Color.parseColor("#8B5394"));
            }

            // Botón eliminar
            h.btnDelete.setOnClickListener(v -> {
                if (deleteListener != null)
                    deleteListener.onDelete(item, h.getAdapterPosition());
            });
        }
    }

    @Override
    public int getItemCount() { return items.size(); }

    // ══════════════════════════════════════════════════════════════════
    // ViewHolders
    // ══════════════════════════════════════════════════════════════════

    // Separador
    static class SeparatorViewHolder extends RecyclerView.ViewHolder {
        TextView tvDate;
        SeparatorViewHolder(@NonNull View v) {
            super(v);
            tvDate = v.findViewById(R.id.tvSeparatorDate);
        }
    }

    // Evento — usa los mismos IDs de tu item_event.xml original
    static class EventViewHolder extends RecyclerView.ViewHolder {
        TextView  tvTitle, tvDescription, tvCategory, tvTime;
        View      statusBar;
        ImageView btnDelete;

        EventViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle       = itemView.findViewById(R.id.tvTitle);
            tvDescription = itemView.findViewById(R.id.tvDescription);
            tvCategory    = itemView.findViewById(R.id.tvCategory);
            tvTime        = itemView.findViewById(R.id.tvTime);
            statusBar     = itemView.findViewById(R.id.statusBar);
            btnDelete     = itemView.findViewById(R.id.btnDelete);
        }
    }
}