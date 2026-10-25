package com.google.mlkit.vision.barcode;

// com.google.mlkit.vision.barcode.BarcodeScanning -- entry-point factory for a
// BarcodeScanner. Both overloads return our Default scanner whose process()
// fails cleanly. Mirrors the published static factory.
// Ref: developers.google.com/android/reference/.../barcode/BarcodeScanning
public final class BarcodeScanning {
    private BarcodeScanning() {}

    public static BarcodeScanner getClient() {
        return new BarcodeScanner.Default();
    }

    public static BarcodeScanner getClient(BarcodeScannerOptions options) {
        return new BarcodeScanner.Default();
    }
}
