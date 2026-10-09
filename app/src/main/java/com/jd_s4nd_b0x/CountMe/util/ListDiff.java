package com.jd_s4nd_b0x.CountMe.util;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * Dispatches fine-grained RecyclerView change events instead of notifyDataSetChanged().
 *
 * <p>Items are compared through snapshots (an id and a content key captured at bind time) rather
 * than through the model objects, because the activities mutate model objects in place before the
 * adapter is refreshed; comparing the objects themselves would then wrongly report "no change".
 */
public final class ListDiff {

    private ListDiff() {}

    public static void update(
            @NonNull RecyclerView.Adapter<?> adapter,
            @NonNull List<String> oldIds,
            @NonNull List<String> oldKeys,
            @NonNull List<String> newIds,
            @NonNull List<String> newKeys) {
        DiffUtil.calculateDiff(
                        new DiffUtil.Callback() {
                            @Override
                            public int getOldListSize() {
                                return oldIds.size();
                            }

                            @Override
                            public int getNewListSize() {
                                return newIds.size();
                            }

                            @Override
                            public boolean areItemsTheSame(int o, int n) {
                                return oldIds.get(o).equals(newIds.get(n));
                            }

                            @Override
                            public boolean areContentsTheSame(int o, int n) {
                                return oldKeys.get(o).equals(newKeys.get(n));
                            }
                        })
                .dispatchUpdatesTo(adapter);
    }
}
