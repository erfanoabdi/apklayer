package com.google.mlkit.vision.barcode;

// com.google.mlkit.vision.barcode.BarcodeScannerOptions -- format-filter options
// passed to BarcodeScanning.getClient(options). Mirrors the published Builder
// (setBarcodeFormats(int, int...) / enableAllPotentialBarcodes). Values are inert
// because no scanner runs. Ref: developers.google.com/android/reference
public class BarcodeScannerOptions {

    private final int formats;

    private BarcodeScannerOptions(int formats) { this.formats = formats; }

    public int getFormats() { return formats; }

    public static class Builder {
        private int formats = com.google.mlkit.vision.barcode.common.Barcode.FORMAT_ALL_FORMATS;
        public Builder setBarcodeFormats(int format, int... moreFormats) {
            int f = format;
            if (moreFormats != null) for (int m : moreFormats) f |= m;
            this.formats = f;
            return this;
        }
        public Builder enableAllPotentialBarcodes() { return this; }
        public BarcodeScannerOptions build() { return new BarcodeScannerOptions(formats); }
    }
}
