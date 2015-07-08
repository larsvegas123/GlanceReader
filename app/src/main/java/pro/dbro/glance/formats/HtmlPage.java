package pro.dbro.glance.formats;

import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.text.Html;
import android.text.TextUtils;

import com.google.gson.JsonObject;
import com.koushikdutta.async.future.FutureCallback;
import com.koushikdutta.ion.Ion;

import java.net.MalformedURLException;
import java.net.URL;

import pro.dbro.glance.http.TrustManager;
import timber.log.Timber;

//import pro.dbro.glance.SECRETS;

/**
 * This provides an implementation of {@link pro.dbro.glance.formats.SpritzerMedia}
 * that serves a web page
 *
 * @author defer (diogo@underdev.org)
 */
public class HtmlPage implements SpritzerMedia {
    public static final boolean VERBOSE = true;

    private static boolean sSetupTrustManager = false;
    /**
     * The logging tag.
     */
    private static final String TAG = "HtmlPage";

    private String mTitle;
    private String mUrl;
    private String mContent;


    /**
     * Builds an HtmlPage from a {@link com.google.gson.JsonObject} in diffbot format.
     * See http://www.diffbot.com/products/automatic/
     *
     * @param result The {@link com.google.gson.JsonObject} to display
     */
    private HtmlPage(JsonObject result) {
        if (result != null)
            initFromJson(result);
    }

    public boolean setResult(JsonObject result) {
        return initFromJson(result);
    }

    private boolean initFromJson(JsonObject json) {
        // Diffbot json format
        // see http://www.diffbot.com/products/automatic/
        if (json == null) {
            Timber.e("Error parsing page");
            return false;
        }
        if (json.has("title"))
            mTitle = json.get("title").getAsString();
        if (json.has("url"))
            mUrl = json.get("url").getAsString();
        if (json.has("text") && !TextUtils.isEmpty(json.get("text").getAsString()))
            mContent = json.get("text").getAsString();
        else {
            Timber.e("Got json response, but it contained no content text");
            return false;
        }

        // Sanitize content
        mContent = Html.fromHtml(mContent).toString().replaceAll("\\n+", " ").replaceAll("(?s)<!--.*?-->", "");
        return true;
    }

    /**
     * Creates an {@link pro.dbro.glance.formats.HtmlPage} from a url.
     * Returns immediately with an {@link pro.dbro.glance.formats.HtmlPage}
     * that is not yet initialized. Pass a {@link pro.dbro.glance.formats.HtmlPage.HtmlPageParsedCallback}
     * to be notified when page parsing is complete, and the returned HtmlPage is populated.
     *
     * @param url The http url.
     * @param cb  A callback to be invoked when the HtmlPage is parsed
     * @return An HtmlPage with null JResult;
     * @throws pro.dbro.glance.formats.UnsupportedFormatException if HTML parsing fails
     */
    public static HtmlPage fromUri(final Context context, String url, final HtmlPageParsedCallback cb) throws UnsupportedFormatException {
        // Seems to be a bug in Ion setting trust manager
        // When that's resolved, go back to Ion request
//        if (!sSetupTrustManager) {
//            sSetupTrustManager = TrustManager.setupIonTrustManager(context);
//        }
        final HtmlPage page = new HtmlPage(null);
        String encodedUrlToParse = Uri.encode(url);
        String requestUrl = String.format("http://api.diffbot.com/v2/article?url=%s&token=%s", encodedUrlToParse, "2efef432c72b5a923408e04353c39a7c");
        Timber.d("Loading url: " + requestUrl);
//        TrustManager.makeTrustRequest(context, requestUrl, new TrustManager.TrustRequestCallback() {
//            @Override
//            public void onSuccess(JsonObject result) {
//                page.setResult(result);
//                recordRead(page);
//
//                if (cb != null) {
//                    cb.onPageParsed(page);
//
//                }
//            }
//        });
        Ion.getInstance(context, TrustManager.sIonInstanceName)
                .build(context)
                .load(requestUrl)
                .asJsonObject()
                .setCallback(new FutureCallback<JsonObject>() {
                    @Override
                    public void onCompleted(Exception e, final JsonObject result) {
                        if (e != null) {
                            Timber.e(e, "Unable to parse page");
                            return;
                        }

                        //Timber.d("Got diffbot result " + result.toString());
                        new AsyncTask<JsonObject, Void, HtmlPage>() {

                            @Override
                            protected HtmlPage doInBackground(JsonObject... params) {

                                JsonObject result = params[0];
                                boolean sucess = page.setResult(result);

                                return sucess ? page : null;
                            }

                            @Override
                            protected void onPostExecute(HtmlPage result) {
                                if (cb != null)
                                    cb.onPageParsed(result);
                            }

                        }.execute(result);
                    }
                });

        return page;
    }


    public String getUrl() {
        return mUrl;
    }

    @Override
    public String getTitle() {
        return (mTitle == null) ? "" : mTitle;
    }

    @Override
    public String getAuthor() {
        try {
            if (mUrl != null)
                return new URL(mUrl).getHost();
            return "";
        } catch (MalformedURLException e) {
            return "";
        }
    }

    @Override
    public String loadChapter(int ignored) {
        return (mContent == null) ? "" : mContent;
    }

    @Override
    public String getChapterTitle(int ignored) {
        return "";
    }


    @Override
    public int countChapters() {
        return 1;
    }

    public static interface HtmlPageParsedCallback {
        public void onPageParsed(HtmlPage result);
    }

}