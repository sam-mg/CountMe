package com.jd_s4nd_b0x.CountMe.util;

import static org.junit.Assert.assertEquals;

import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class ListDiffTest {

    private static final class Recorder extends RecyclerView.AdapterDataObserver {
        final List<String> events = new ArrayList<>();

        @Override
        public void onItemRangeChanged(int start, int count, Object payload) {
            events.add("changed " + start + "+" + count);
        }

        @Override
        public void onItemRangeInserted(int start, int count) {
            events.add("inserted " + start + "+" + count);
        }

        @Override
        public void onItemRangeRemoved(int start, int count) {
            events.add("removed " + start + "+" + count);
        }
    }

    private static final class Dummy extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getItemCount() {
            return 0;
        }
    }

    private static List<String> l(String... v) {
        return Arrays.asList(v);
    }

    private static List<String> diff(
            List<String> oi, List<String> ok, List<String> ni, List<String> nk) {
        Dummy adapter = new Dummy();
        Recorder rec = new Recorder();
        adapter.registerAdapterDataObserver(rec);
        ListDiff.update(adapter, oi, ok, ni, nk);
        return rec.events;
    }

    @Test
    public void identicalListsProduceNoEvents() {
        assertEquals(l(), diff(l("a", "b"), l("a1", "b1"), l("a", "b"), l("a1", "b1")));
    }

    @Test
    public void contentChangeOnlyRebindsThatRow() {
        assertEquals(
                l("changed 1+1"), diff(l("a", "b"), l("a1", "b1"), l("a", "b"), l("a1", "b2")));
    }

    @Test
    public void insertionAndRemovalAreReportedPrecisely() {
        assertEquals(
                l("inserted 2+1"),
                diff(l("a", "b"), l("1", "1"), l("a", "b", "c"), l("1", "1", "1")));
        assertEquals(l("removed 0+1"), diff(l("a", "b"), l("1", "1"), l("b"), l("1")));
    }
}
