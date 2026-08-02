package android.print;

import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import java.io.File;

public class PrintHelper {
    public interface PrintCallback {
        void onSuccess();
        void onFailure(String error);
    }

    public static void savePdf(final PrintDocumentAdapter adapter, final File file, final PrintCallback callback) {
        PrintAttributes printAttributes = new PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setResolution(new PrintAttributes.Resolution("pdf", "pdf", 300, 300))
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build();

        adapter.onLayout(null, printAttributes, new CancellationSignal(), new PrintDocumentAdapter.LayoutResultCallback() {
            @Override
            public void onLayoutFinished(PrintDocumentInfo info, boolean changed) {
                try {
                    final ParcelFileDescriptor pfd = ParcelFileDescriptor.open(file, 
                        ParcelFileDescriptor.MODE_READ_WRITE | ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE);
                    
                    adapter.onWrite(new PageRange[]{PageRange.ALL_PAGES}, pfd, new CancellationSignal(), new PrintDocumentAdapter.WriteResultCallback() {
                        @Override
                        public void onWriteFinished(PageRange[] pages) {
                            try {
                                pfd.close();
                                callback.onSuccess();
                            } catch (Exception e) {
                                callback.onFailure(e.getLocalizedMessage());
                            }
                        }

                        @Override
                        public void onWriteFailed(CharSequence error) {
                            try {
                                pfd.close();
                            } catch (Exception e) {}
                            callback.onFailure(error != null ? error.toString() : "Write failed");
                        }

                        @Override
                        public void onWriteCancelled() {
                            try {
                                pfd.close();
                            } catch (Exception e) {}
                            callback.onFailure("Write cancelled");
                        }
                    });
                } catch (Exception e) {
                    callback.onFailure(e.getLocalizedMessage());
                }
            }

            @Override
            public void onLayoutFailed(CharSequence error) {
                callback.onFailure(error != null ? error.toString() : "Layout failed");
            }

            @Override
            public void onLayoutCancelled() {
                callback.onFailure("Layout cancelled");
            }
        }, null);
    }
}
