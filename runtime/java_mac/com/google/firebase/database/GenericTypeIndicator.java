package com.google.firebase.database;

// com.google.firebase.database.GenericTypeIndicator<T> -- used by apps to read
// generic collections out of a DataSnapshot (e.g.
// new GenericTypeIndicator<List<Foo>>() {}). Real Firebase reflects on the
// type-token's superclass to recover the parameterized type at runtime. Our
// offline shim never returns data, so this only needs to be referenceable and
// instantiable as an anonymous subclass; we keep it abstract to match the
// published API (it cannot be constructed directly).
public abstract class GenericTypeIndicator<T> {
    public GenericTypeIndicator() {}
}
