/*
 *  Copyright 2016 Google Inc. All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.google.android.apps.forscience.whistlepunk.devicemanager;

import android.support.v7.widget.RecyclerView;
import android.util.SparseArray;
import android.view.ViewGroup;

/**
 * Takes any number of child RecyclerView.Adapters, and concatenates them into a single dataset.
 *
 * All child adapters must only use the same type identifiers from getItemViewType if they indeed
 * can share ViewHolder implementations.  I recommend using resource ids of some kind to help
 * enforce this.
 */
public class CompositeRecyclerAdapter<VH extends RecyclerView.ViewHolder> extends
        RecyclerView.Adapter<VH> {
    private RecyclerView.Adapter<VH>[] mSubAdapters;
    private SparseArray<Integer> mViewTypeToCreatingAdapterIndex = new SparseArray<>();

    /**
     * See scanTo for how these work
     */
    private int mLastGlobalPosition = -1;
    private int mLastSubAdapterIndex = -1;
    private int mLastSubPosition = -1;

    /**
     * CompositeRecyclerAdapter takes incoming requests and farms them out to child adapters.  To do
     * this, we need to know which child adapter is responsible for which child item.  To act on
     * the item at global position {@code position}, call {@code scanTo(position)}, and then use
     * mSubAdapters[mLastSubAdapterIndex] to get the correct child adapter, and mLastSubPosition to
     * get the index of the child item within that child adapter.
     */
    private void scanTo(int position) {
        if (position == mLastGlobalPosition) {
            return;
        }

        int subPosition = position;
        int index = 0;
        while (index < mSubAdapters.length) {
            RecyclerView.Adapter<VH> sub = mSubAdapters[index];
            int subCount = sub.getItemCount();
            if (subPosition < subCount) {
                mLastGlobalPosition = position;
                mLastSubAdapterIndex = index;
                mLastSubPosition = subPosition;
                return;
            }
            subPosition -= subCount;
            index++;
        }
        throw new IndexOutOfBoundsException("Asked for item " + position + " of " + getItemCount());
    }

    public CompositeRecyclerAdapter(RecyclerView.Adapter<VH>... subAdapters) {
        mSubAdapters = subAdapters;
        for (int i = 0; i < subAdapters.length; i++) {
            final int thisSubIndex = i;
            subAdapters[i].registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
                @Override
                public void onChanged() {
                    notifyDataSetChanged();
                }

                @Override
                public void onItemRangeChanged(int positionStart, int itemCount) {
                    notifyItemRangeChanged(translate(thisSubIndex, positionStart), itemCount);
                }

                @Override
                public void onItemRangeChanged(int positionStart, int itemCount, Object payload) {
                    notifyItemRangeChanged(translate(thisSubIndex, positionStart), itemCount, payload);
                }

                @Override
                public void onItemRangeInserted(int positionStart, int itemCount) {
                    notifyItemRangeInserted(translate(thisSubIndex, positionStart), itemCount);
                }

                @Override
                public void onItemRangeRemoved(int positionStart, int itemCount) {
                    notifyItemRangeRemoved(translate(thisSubIndex, positionStart), itemCount);
                }

                @Override
                public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) {
                    notifyItemMoved(translate(thisSubIndex, fromPosition),
                            translate(thisSubIndex, toPosition));
                }
            });
        }
    }

    private int translate(int subIndex, int subPosition) {
        int position = subPosition;
        for (int i = 0; i < subIndex; i++) {
            position += mSubAdapters[i].getItemCount();
        }
        return position;
    }

    @Override
    public int getItemViewType(int position) {
        scanTo(position);
        int itemViewType = mSubAdapters[mLastSubAdapterIndex].getItemViewType(mLastSubPosition);
        mViewTypeToCreatingAdapterIndex.put(itemViewType, mLastSubAdapterIndex);
        return itemViewType;
    }

    @Override
    public VH onCreateViewHolder(ViewGroup parent, int viewType) {
        return mSubAdapters[mViewTypeToCreatingAdapterIndex.get(viewType)].onCreateViewHolder(
                parent, viewType);
    }

    @Override
    public void onBindViewHolder(VH holder, int position) {
        scanTo(position);
        mSubAdapters[mLastSubAdapterIndex].onBindViewHolder(holder, mLastSubPosition);
    }

    @Override
    public int getItemCount() {
        // TODO: would it help to cache this?
        int count = 0;
        for (RecyclerView.Adapter<VH> sub : mSubAdapters) {
            count += sub.getItemCount();
        }
        return count;
    }
}
