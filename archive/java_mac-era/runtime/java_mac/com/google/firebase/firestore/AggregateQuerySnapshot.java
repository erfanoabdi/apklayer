package com.google.firebase.firestore;

// com.google.firebase.firestore.AggregateQuerySnapshot -- the result of an
// AggregateQuery.get(). On the offline-only shim getCount() is 0 (empty store).
public class AggregateQuerySnapshot {

    private final AggregateQuery query;
    private final long count;

    AggregateQuerySnapshot(AggregateQuery query, long count) {
        this.query = query;
        this.count = count;
    }

    public AggregateQuery getQuery() { return query; }

    public long getCount() { return count; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AggregateQuerySnapshot)) return false;
        AggregateQuerySnapshot s = (AggregateQuerySnapshot) o;
        return count == s.count && query.equals(s.query);
    }

    @Override
    public int hashCode() {
        return 31 * query.hashCode() + (int) (count ^ (count >>> 32));
    }
}
