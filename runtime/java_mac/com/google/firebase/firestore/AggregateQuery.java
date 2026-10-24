package com.google.firebase.firestore;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

// com.google.firebase.firestore.AggregateQuery -- a count() over a Query. On
// our offline-only shim the underlying query is empty, so the aggregate
// resolves to count=0 (honest negative result for an empty store, never a
// fabricated total).
public class AggregateQuery {

    private final Query query;

    AggregateQuery(Query query) {
        this.query = query;
    }

    public Query getQuery() { return query; }

    public Task<AggregateQuerySnapshot> get(AggregateSource source) {
        return Tasks.forResult(new AggregateQuerySnapshot(this, 0L));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AggregateQuery)) return false;
        return query.equals(((AggregateQuery) o).query);
    }

    @Override
    public int hashCode() { return query.hashCode(); }
}
