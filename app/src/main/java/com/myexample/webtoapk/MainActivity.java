package com.myexample.webtoapk;

import android.content.DialogInterface;
import android.net.http.SslError;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import android.os.Bundle;
import android.view.KeyEvent;
import android.webkit.SslErrorHandler;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.os.Handler;
import android.webkit.WebSettings;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.content.Intent;
import android.net.Uri;
import android.content.res.Configuration;
import android.widget.EditText;
import android.webkit.JsResult;
import android.webkit.JsPromptResult;
import android.widget.FrameLayout;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.graphics.Bitmap;
import android.util.Log;
import android.webkit.ConsoleMessage;
import android.graphics.Color;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebResourceError;
import androidx.annotation.Nullable;
import java.io.ByteArrayInputStream;
import android.webkit.JavascriptInterface;
import android.content.Context;
import android.content.ActivityNotFoundException;
import android.os.Looper;
import android.webkit.GeolocationPermissions;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.widget.TextView;
import android.app.Activity;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient.FileChooserParams;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.activity.OnBackPressedCallback;
import android.webkit.DownloadListener;
import android.provider.MediaStore;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import androidx.core.content.FileProvider;
import android.app.DownloadManager;
import android.webkit.URLUtil;
import android.os.Environment;
import static android.content.Context.DOWNLOAD_SERVICE;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import org.unifiedpush.android.connector.UnifiedPush;
import static org.unifiedpush.android.connector.ConstantsKt.INSTANCE_DEFAULT;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import org.json.JSONException;
import android.app.AlarmManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import androidx.annotation.NonNull;
import androidx.core.view.WindowCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import android.graphics.Color;
import androidx.core.graphics.Insets;
import android.view.WindowInsetsController;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.webkit.PermissionRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;


public class MainActivity extends AppCompatActivity {
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 2;
    private static final int MEDIA_PERMISSION_REQUEST_CODE = 1001;
    private static final String NOTIFICATION_CHANNEL_ID = "web_app_notifications";
    private static final String NOTIFICATION_CHANNEL_NAME = "Web App Notifications";

    private WebView webview;
    private UserScriptManager userScriptManager;
    private ProgressBar spinner;
    private View mainLayout;
    private View errorLayout;
    private ViewGroup parentLayout;
    private boolean errorOccurred = false; // For WebView after tryAgain
    private ValueCallback<Uri[]> mFilePathCallback;       // Image upload
    private ActivityResultLauncher<Intent> fileChooserLauncher; // Image upload
    private Uri pendingCameraUri; // URI for camera capture
    private WebAppInterface webAppInterface;
    private BroadcastReceiver unifiedPushEndpointReceiver;
    private BroadcastReceiver mediaActionReceiver;
    private BroadcastReceiver notificationEventReceiver;
    private PermissionRequest currentPermissionRequest;
    private GeolocationPermissions.Callback geoCallback;
    private String geoOrigin;

    String mainURL = "https://github.com/Jipok";
    boolean requireDoubleBackToExit = true;
    boolean allowSubdomains = true;

    boolean enableExternalLinks = true;
    boolean openExternalLinksInBrowser = true;
    boolean confirmOpenInBrowser = true;

    boolean allowOpenMobileApp = false;
    boolean confirmOpenExternalApp = true;

    String cookies = "";
    String basicAuth = "";
    String userAgent = "";
    boolean blockLocalhostRequests = true;
    boolean JSEnabled = true;
    boolean JSCanOpenWindowsAutomatically = true;
    boolean DomStorageEnabled = true;
    boolean DatabaseEnabled = true;
    boolean MediaPlaybackRequiresUserGesture = true;
    boolean SavePassword = true;
    boolean AllowFileAccess = true;
    boolean AllowFileAccessFromFileURLs = true;
    boolean showDetailsOnErrorScreen = false;
    boolean forceLandscapeMode = false;
    boolean edgeToEdge = false;
    boolean forceDarkTheme = false;
    boolean allowMixedContent = false;
    String cacheMode = "default";
    int fadeInDuration = 400;
    boolean DebugWebView = false;

    boolean geolocationEnabled = false;
    boolean cameraEnabled = false;
    boolean microphoneEnabled = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (forceDarkTheme) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        }
        if (edgeToEdge) {
            WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        }

        super.onCreate(savedInstanceState);

        if (edgeToEdge) {
            getWindow().setStatusBarColor(Color.TRANSPARENT);
            getWindow().setNavigationBarColor(Color.TRANSPARENT);
        }

        // Create the NotificationChannel, but only on API 26+
        NotificationHelper.createChannels(this);

        if (forceLandscapeMode) {
            setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }

        setContentView(R.layout.activity_main);
        mainLayout = findViewById(android.R.id.content);
        parentLayout = (ViewGroup) mainLayout.getParent();
        userScriptManager = new UserScriptManager(this, mainURL);

        // Handle intent
        Intent intent = getIntent();
        String action = intent.getAction();
        Uri data = intent.getData();
        Log.d("WebToApk", "Action: " + action);
        Log.d("WebToApk", "Data: " + data);
        if (Intent.ACTION_VIEW.equals(action) && data != null) {
            mainURL = data.toString();
        }

        webview = findViewById(R.id.webView);
        webview.setAlpha(0f);
        spinner = findViewById(R.id.progressBar1);
        webview.setWebViewClient(new CustomWebViewClient());
        webview.setWebChromeClient(new CustomWebChrome());
        webAppInterface = new WebAppInterface(this);
        webview.addJavascriptInterface(webAppInterface, "WebToApk");

        WebSettings webSettings = webview.getSettings();
        webSettings.setJavaScriptEnabled(JSEnabled);
        webSettings.setJavaScriptCanOpenWindowsAutomatically(JSCanOpenWindowsAutomatically);
        webSettings.setGeolocationEnabled(geolocationEnabled);
        webSettings.setDomStorageEnabled(DomStorageEnabled);
        webSettings.setDatabaseEnabled(DatabaseEnabled);
        webSettings.setMediaPlaybackRequiresUserGesture(MediaPlaybackRequiresUserGesture);
        // setSavePassword is a no-op since API 18 and removed in API 33
        webSettings.setAllowFileAccess(AllowFileAccess);
        // setAllowFileAccessFromFileURLs is deprecated; file:// cross-origin is blocked by WebView security by default
        webSettings.setUseWideViewPort(true);
        webSettings.setLoadWithOverviewMode(true);
        webview.setWebContentsDebuggingEnabled(DebugWebView);

        if (allowMixedContent) {
            webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        if (!userAgent.isEmpty()) {
            webSettings.setUserAgentString(userAgent);
        }

        switch (cacheMode) {
            case "aggressive":
                // Offline-first: Use cache if content is there, otherwise load from network.
                webSettings.setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
                break;
            case "no_cache":
                // Always load from the network, do not use the cache
                webSettings.setCacheMode(WebSettings.LOAD_NO_CACHE);
                webview.clearCache(true);
                break;
            default:
                // Uses cache based on server's "Cache-Control" headers
                webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
                break;
        }

        webview.setOverScrollMode(WebView.OVER_SCROLL_NEVER);

        CookieManager cookieManager = CookieManager.getInstance();
        CookieManager.getInstance().setAcceptThirdPartyCookies(webview, true);
        cookieManager.setCookie(mainURL, cookies);
        cookieManager.flush();

        // Image upload support
        fileChooserLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                Uri[] results = null;

                if (result.getResultCode() == Activity.RESULT_OK) {
                    Intent intentData = result.getData();
                    if (intentData != null && intentData.getClipData() != null) {
                        // Multiple file selection
                        int count = intentData.getClipData().getItemCount();
                        results = new Uri[count];
                        for (int i = 0; i < count; i++) {
                            results[i] = intentData.getClipData().getItemAt(i).getUri();
                        }
                    } else if (intentData != null && intentData.getData() != null) {
                        // Single file from gallery
                        results = new Uri[]{intentData.getData()};
                    } else if (pendingCameraUri != null) {
                        // Photo taken with camera (no data intent, output went to pendingCameraUri)
                        results = new Uri[]{pendingCameraUri};
                    }
                }
                pendingCameraUri = null;

                if (mFilePathCallback != null) {
                    mFilePathCallback.onReceiveValue(results);
                    mFilePathCallback = null;
                }
            }
        );

        // File downloading support
        webview.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
                DownloadManager downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);

                // Create a request for the download
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                request.setMimeType(mimetype);

                // Set cookies for the download request, it's important for authorized downloads
                String cookies = CookieManager.getInstance().getCookie(url);
                request.addRequestHeader("cookie", cookies);
                request.addRequestHeader("User-Agent", userAgent);

                // Set download description and title using string resources
                request.setDescription(getString(R.string.download_description)); // Use string resource
                request.setTitle(URLUtil.guessFileName(url, contentDisposition, mimetype));

                // Show notification during and after download
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

                // Set the destination directory for the downloaded file
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(url, contentDisposition, mimetype));

                try {
                    downloadManager.enqueue(request);
                    Toast.makeText(getApplicationContext(), R.string.download_started, Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Toast.makeText(getApplicationContext(), R.string.download_failed, Toast.LENGTH_LONG).show();
                    Log.e("WebToApk", "Failed to start download", e);
                }
            }
        });


        // Broadcast receiver to get the endpoint from the PushServiceImpl
        unifiedPushEndpointReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // Now we receive all parts of the subscription
                String endpoint = intent.getStringExtra("endpoint");
                String p256dh = intent.getStringExtra("p256dh");
                String auth = intent.getStringExtra("auth");

                Log.d("WebToApk", "Received new UnifiedPush data. Endpoint: " + endpoint);

                // Instead of a simple function call, we now create the full subscription JSON
                // and pass it to a special function in our shim that will resolve the 'subscribe()' promise.
                if (endpoint != null && p256dh != null && auth != null && webview != null) {
                    try {
                        JSONObject keys = new JSONObject();
                        // Use the real keys received from the distributor
                        keys.put("p256dh", p256dh);
                        keys.put("auth", auth);

                        JSONObject subscription = new JSONObject();
                        subscription.put("endpoint", endpoint);
                        subscription.put("expirationTime", JSONObject.NULL);
                        subscription.put("keys", keys);

                        String subscriptionJson = subscription.toString();

                        webview.post(() -> {
                            // This JS function is defined in our new shim
                            String js = "if (typeof window.__shim_onNewEndpoint === 'function') { window.__shim_onNewEndpoint('" + subscriptionJson.replace("'", "\\'") + "'); }";
                            webview.evaluateJavascript(js, null);
                        });

                    } catch (JSONException e) {
                         Log.e("WebToApk", "Failed to create subscription JSON for shim", e);
                    }
                }
            }
        };
        // Register the receiver with compatibility for different Android versions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(unifiedPushEndpointReceiver, new IntentFilter(BuildConfig.APPLICATION_ID + ".NEW_ENDPOINT"), RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(unifiedPushEndpointReceiver, new IntentFilter(BuildConfig.APPLICATION_ID + ".NEW_ENDPOINT"));
        }

        if (edgeToEdge) {
            ViewCompat.setOnApplyWindowInsetsListener(mainLayout, (v, windowInsets) -> {
                // Get the insets for system bars in hardware pixels.
                Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());

                // Get the device's screen density factor.
                float density = v.getResources().getDisplayMetrics().density;

                // Convert hardware pixels to density-independent CSS pixels.
                float top = insets.top / density;
                float bottom = insets.bottom / density;
                float left = insets.left / density;
                float right = insets.right / density;

                Log.d("WebToApk", String.format(java.util.Locale.US,
                    "Insets (CSS px) -> T:%.2f, B:%.2f, L:%.2f, R:%.2f",
                    top, bottom, left, right
                ));

                // Pass insets to WebView via CSS custom properties.
                // These names are chosen to be close to the standard CSS env() variables.
                // The web content can then use var(--safe-area-inset-top).
                String js = String.format(java.util.Locale.US,
                    "document.documentElement.style.setProperty('--safe-area-inset-top', '%.2fpx');" +
                    "document.documentElement.style.setProperty('--safe-area-inset-bottom', '%.2fpx');" +
                    "document.documentElement.style.setProperty('--safe-area-inset-left', '%.2fpx');" +
                    "document.documentElement.style.setProperty('--safe-area-inset-right', '%.2fpx');" +
                    "document.dispatchEvent(new CustomEvent('WebToApkInsetsApplied'));",
                    top, bottom, left, right
                );
                webview.evaluateJavascript(js, null);

                return WindowInsetsCompat.CONSUMED;
            });
        }

        // TODO check https://stackoverflow.com/questions/18479519/how-to-save-restore-webview-state
        boolean stateRestored = false;
        if (savedInstanceState != null) {
            // Restore the state of the WebView from the saved bundle.
            stateRestored = webview.restoreState(savedInstanceState) != null;
            if (stateRestored) Log.d("WebToApk", "Restored WebView state");
        }

        if (!stateRestored) {
            // It's a fresh launch. Or broken old. Load the main URL.
            webview.loadUrl(mainURL);
        }

        mediaActionReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent != null && MediaPlaybackService.BROADCAST_MEDIA_ACTION.equals(intent.getAction())) {
                    String action = intent.getStringExtra(MediaPlaybackService.EXTRA_MEDIA_ACTION);
                    if (action != null) {
                        executeMediaActionInWebView(action);
                    }
                }
            }
        };
        LocalBroadcastManager.getInstance(this).registerReceiver(mediaActionReceiver, new IntentFilter(MediaPlaybackService.BROADCAST_MEDIA_ACTION));
        setupBackHandler();

        // Receive notification tap / action / dismiss events from NotificationReceiver
        notificationEventReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action == null) return;
                try {
                    org.json.JSONObject obj = new org.json.JSONObject();
                    obj.put("payload", intent.getStringExtra(NotificationReceiver.EXTRA_PAYLOAD) != null
                            ? intent.getStringExtra(NotificationReceiver.EXTRA_PAYLOAD) : "");
                    obj.put("title", intent.getStringExtra(NotificationReceiver.EXTRA_TITLE) != null
                            ? intent.getStringExtra(NotificationReceiver.EXTRA_TITLE) : "");
                    obj.put("message", intent.getStringExtra(NotificationReceiver.EXTRA_MESSAGE) != null
                            ? intent.getStringExtra(NotificationReceiver.EXTRA_MESSAGE) : "");

                    if (NotificationReceiver.ACTION_NOTIFICATION_TAPPED.equals(action)) {
                        fireJsCallbackObj("onNotificationTapped", obj.toString());
                    } else if (NotificationReceiver.ACTION_NOTIFICATION_ACTION.equals(action)) {
                        obj.put("actionLabel", intent.getStringExtra(NotificationReceiver.EXTRA_ACTION_LABEL) != null
                                ? intent.getStringExtra(NotificationReceiver.EXTRA_ACTION_LABEL) : "");
                        obj.put("actionIndex", intent.getIntExtra(NotificationReceiver.EXTRA_ACTION_INDEX, 0));
                        fireJsCallbackObj("onNotificationAction", obj.toString());
                    } else if (NotificationReceiver.ACTION_DISMISS_NOTIFICATION.equals(action)) {
                        fireJsCallbackObj("onNotificationDismissed", obj.toString());
                    }
                } catch (Exception e) {
                    Log.e("WebToApk", "notificationEventReceiver error", e);
                }
            }
        };
        IntentFilter notifFilter = new IntentFilter();
        notifFilter.addAction(NotificationReceiver.ACTION_NOTIFICATION_TAPPED);
        notifFilter.addAction(NotificationReceiver.ACTION_NOTIFICATION_ACTION);
        notifFilter.addAction(NotificationReceiver.ACTION_DISMISS_NOTIFICATION);
        LocalBroadcastManager.getInstance(this).registerReceiver(notificationEventReceiver, notifFilter);

    }

    private void registerForUnifiedPush(final String vapidPublicKey) {
        if (vapidPublicKey == null || vapidPublicKey.isEmpty()) {
            Log.e("WebToApk", "VAPID public key is null or empty. Cannot register for push.");
            return;
        }

        UnifiedPush.tryUseCurrentOrDefaultDistributor(this, new Function1<Boolean, Unit>() {
            @Override
            public Unit invoke(Boolean success) {
                if (success) {
                    Log.d("WebToApk", "UnifiedPush distributor found, registering...");
                    UnifiedPush.register(
                        MainActivity.this,
                        INSTANCE_DEFAULT,
                        null,
                        vapidPublicKey
                    );
                } else {
                    Log.w("WebToApk", "No UnifiedPush distributor found or user cancelled.");

                    // We must run UI and WebView operations on the main thread
                    new Handler(Looper.getMainLooper()).post(() -> {
                        // Show an informative dialog to the user
                        new AlertDialog.Builder(MainActivity.this)
                            .setTitle(R.string.push_distributor_required_title)
                            .setMessage(R.string.push_distributor_required_message)
                            .setPositiveButton(R.string.learn_more, (dialog, which) -> {
                                // Open the UnifiedPush website for users to find distributors
                                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://unifiedpush.org/users/distributors/"));
                                startActivity(browserIntent);
                            })
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                    });
                }
                return Unit.INSTANCE;
            }
        });
    }

    private void executeMediaActionInWebView(String action) {
        Log.d("WebToApk", "Executing JS for media action: " + action);
        if (webview != null) {
            webview.post(() -> {
                String js = "if (typeof window.__runMediaAction === 'function') { window.__runMediaAction('" + action + "'); }";
                webview.evaluateJavascript(js, null);
            });
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (unifiedPushEndpointReceiver != null) {
            unregisterReceiver(unifiedPushEndpointReceiver);
        }
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mediaActionReceiver);
        if (notificationEventReceiver != null) LocalBroadcastManager.getInstance(this).unregisterReceiver(notificationEventReceiver);
        Intent intent = new Intent(this, MediaPlaybackService.class);
        stopService(intent);
    }

    // Save the state of the WebView (current URL, history, scroll position) during OOM kill
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (webview != null) webview.saveState(outState);
    }

    // TODO idk what do with this. rly need?
    // @Override
    // protected void onPause() {
    //     super.onPause();
    //     Log.d("WebToApk", "onPause");
    //     if (webview != null) {
    //         Log.d("WebToApk", "onPause 2");
    //         webview.onPause();
    //     }
    // }
    // @Override
    // protected void onResume() {
    //     super.onResume();
    //     Log.d("WebToApk", "onResume");
    //     if (webview != null) {
    //         Log.d("WebToApk", "onResume 2");
    //         webview.onResume();
    //     }
    // }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        // Check if ALL permissions in the array are granted
        boolean isGranted = true;
        if (grantResults.length == 0) {
            isGranted = false;
        } else {
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    isGranted = false;
                    break;
                }
            }
        }

        // Handle Camera/Mic
        if (requestCode == MEDIA_PERMISSION_REQUEST_CODE) {
            if (currentPermissionRequest != null) {
                if (isGranted) {
                    currentPermissionRequest.grant(currentPermissionRequest.getResources());
                } else {
                    currentPermissionRequest.deny();
                }
                currentPermissionRequest = null;
            }
        }
        // Handle Location (Re-using the ID or creating a new one)
        else if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (geoCallback != null) {
                if (isGranted) {
                    geoCallback.invoke(geoOrigin, true, false);
                } else {
                    geoCallback.invoke(geoOrigin, false, false);
                }
                geoCallback = null;
                geoOrigin = null;
            }
        } else if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            fireJsCallback("onNotificationPermissionResult", isGranted ? "true" : "false");
        }
    }


    /* This allows:
        Remove "Confirm URL" title from js log/alert/dialog/confirm
        Open HTML5 video in fullscreen
    */
    private class CustomWebChrome extends WebChromeClient {

        @Override
        public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
            String src = consoleMessage.sourceId();
            Integer line = consoleMessage.lineNumber();
            String msg = consoleMessage.message();

            if (src.startsWith("http://") || src.startsWith("https://")) {
                src = src.substring(8);
                Log.e("WebToApk", "[" + src + ":" + line + "] " + msg);
            } else {
                // User scripts colorful
                switch (consoleMessage.messageLevel()) {
                    case ERROR:
                        Log.e("WebToApk", "\033[0;31m[" + src + ":" + line  +"] " + msg + "\033[0m");
                        break;
                    case WARNING:
                        Log.w("WebToApk", "\033[1;33m[" + src + ":" +  line +"]\033[0m " + msg);
                        break;
                    case LOG:
                    case DEBUG:
                    case TIP:
                        Log.d("WebToApk", "\033[0;34m[" + src + ":" +  line +"]\033[0m " + msg);
                        break;
                }
            }
            return true;
        }

        @Override
        public boolean onJsAlert(WebView view, String url, String message, final android.webkit.JsResult result) {
            new AlertDialog.Builder(MainActivity.this)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        result.confirm();
                    }
                })
                .setCancelable(false)
                .create()
                .show();
            return true;
        }

        @Override
        public boolean onJsConfirm(WebView view, String url, String message, final JsResult result) {
            new AlertDialog.Builder(MainActivity.this)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        result.confirm();
                    }
                })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        result.cancel();
                    }
                })
                .setCancelable(false)
                .create()
                .show();
            return true;
        }

        @Override
        public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, final JsPromptResult result) {
            final EditText input = new EditText(MainActivity.this);
            input.setText(defaultValue);

            new AlertDialog.Builder(MainActivity.this)
                .setMessage(message)
                .setView(input)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        result.confirm(input.getText().toString());
                    }
                })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        result.cancel();
                    }
                })
                .setCancelable(false)
                .create()
                .show();
            return true;
        }

        @Override
        public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
            if (!geolocationEnabled) {
                callback.invoke(origin, false, false);
                return;
            }

            if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // Permission missing. Save callback and ask User.
                geoCallback = callback;
                geoOrigin = origin;
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
            } else {
                // Permission already granted by OS. Proceed.
                callback.invoke(origin, true, false);
            }
        }


        //////////////////////
        private View mCustomView;
        private WebChromeClient.CustomViewCallback mCustomViewCallback;
        private int mOriginalOrientation;
        private int mOriginalSystemUiVisibility;

        @Override
        public void onHideCustomView() {
            ((FrameLayout)getWindow().getDecorView()).removeView(mCustomView);
            mCustomView = null;
            // Restore system UI visibility
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowInsetsController controller = getWindow().getInsetsController();
                if (controller != null) {
                    controller.show(android.view.WindowInsets.Type.systemBars());
                    controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_DEFAULT);
                }
            } else {
                getWindow().getDecorView().setSystemUiVisibility(mOriginalSystemUiVisibility);
            }
            setRequestedOrientation(mOriginalOrientation);
            mCustomViewCallback.onCustomViewHidden();
            mCustomViewCallback = null;
        }

        @Override
        public void onShowCustomView(View view, WebChromeClient.CustomViewCallback callback) {
            if (mCustomView != null) {
                onHideCustomView();
                return;
            }
            mCustomView = view;
            mOriginalSystemUiVisibility = getWindow().getDecorView().getSystemUiVisibility();
            mOriginalOrientation = getRequestedOrientation();
            mCustomViewCallback = callback;
            ((FrameLayout)getWindow().getDecorView()).addView(mCustomView,
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
            // Enter fullscreen using modern API (API 30+) with fallback
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowInsetsController controller = getWindow().getInsetsController();
                if (controller != null) {
                    controller.hide(android.view.WindowInsets.Type.systemBars());
                    controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                }
            } else {
                getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_IMMERSIVE);
            }
        }

        @Override
        public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
            // Закрываем предыдущий callback если есть
            if (mFilePathCallback != null) {
                mFilePathCallback.onReceiveValue(null);
            }
            mFilePathCallback = filePathCallback;

            // For image-only requests, offer both gallery and camera
            String[] acceptTypes = fileChooserParams.getAcceptTypes();
            boolean isImageOnly = acceptTypes != null && acceptTypes.length == 1
                && "image/*".equals(acceptTypes[0]);

            if (isImageOnly && cameraEnabled) {
                // Create temp file for camera output
                try {
                    File photoFile = File.createTempFile(
                        "IMG_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()),
                        ".jpg",
                        getCacheDir()
                    );
                    pendingCameraUri = FileProvider.getUriForFile(
                        MainActivity.this,
                        getApplicationContext().getPackageName() + ".provider",
                        photoFile
                    );
                } catch (Exception e) {
                    Log.e("WebToApk", "Could not create temp file for camera", e);
                    pendingCameraUri = null;
                }

                Intent galleryIntent = new Intent(Intent.ACTION_GET_CONTENT);
                galleryIntent.addCategory(Intent.CATEGORY_OPENABLE);
                galleryIntent.setType("image/*");

                Intent chooserIntent = Intent.createChooser(galleryIntent, getString(R.string.choose_file));
                if (pendingCameraUri != null) {
                    Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, pendingCameraUri);
                    chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{cameraIntent});
                }
                try {
                    fileChooserLauncher.launch(chooserIntent);
                } catch (ActivityNotFoundException e) {
                    mFilePathCallback = null;
                    Toast.makeText(MainActivity.this, R.string.file_manager_unavailable, Toast.LENGTH_LONG).show();
                    return false;
                }
                return true;
            }

            // Build a proper MIME type from the accept types the page requests
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);

            String[] acceptTypes = fileChooserParams.getAcceptTypes();
            if (acceptTypes != null && acceptTypes.length > 0 && acceptTypes[0] != null && !acceptTypes[0].isEmpty()) {
                // Join multiple accept types (e.g. "image/*,video/*") into a single MIME string
                String mimeType = String.join(",", acceptTypes);
                if (acceptTypes.length > 1) {
                    // Multiple types require */* with extra MIME type filtering
                    intent.setType("*/*");
                    intent.putExtra(Intent.EXTRA_MIME_TYPES, acceptTypes);
                } else {
                    intent.setType(acceptTypes[0]);
                }
            } else {
                intent.setType("*/*");
            }

            if (fileChooserParams.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE) {
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            }

            Intent chooserIntent = Intent.createChooser(intent, getString(R.string.choose_file));

            try {
                fileChooserLauncher.launch(chooserIntent);
            } catch (ActivityNotFoundException e) {
                mFilePathCallback = null;
                Toast.makeText(MainActivity.this, R.string.file_manager_unavailable, Toast.LENGTH_LONG).show();
                return false;
            }

            return true;
        }

        @Override
        public void onPermissionRequest(final PermissionRequest request) {
            // Check if feature is enabled in config
            // If the site asks for video but cameraEnabled is false -> deny immediately
            for (String resource : request.getResources()) {
                if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource) && !cameraEnabled) {
                    request.deny();
                    return;
                }
                if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource) && !microphoneEnabled) {
                    request.deny();
                    return;
                }
            }

            // Calculate which Android permissions correspond to the requested WebView resources
            List<String> permissionsNeeded = new ArrayList<>();
            for (String resource : request.getResources()) {
                if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)) {
                    if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                        permissionsNeeded.add(Manifest.permission.CAMERA);
                    }
                }
                if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)) {
                    if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        permissionsNeeded.add(Manifest.permission.RECORD_AUDIO);
                    }
                }
            }

            if (permissionsNeeded.isEmpty()) {
                // We already have all OS permissions, grant Web permission immediately
                request.grant(request.getResources());
            } else {
                // We are missing OS permissions. Save the request and ask the user.
                currentPermissionRequest = request;
                ActivityCompat.requestPermissions(MainActivity.this, permissionsNeeded.toArray(new String[0]), MEDIA_PERMISSION_REQUEST_CODE);
            }
        }

        @Override
        public void onPermissionRequestCanceled(PermissionRequest request) {
            super.onPermissionRequestCanceled(request);
            currentPermissionRequest = null;
        }
    }


    /**
     * This allows for a splash screen
     * Hide elements once the page loads
     * Show custom error page
     * Resolve issue with SSL certificate
     **/
    private class CustomWebViewClient extends WebViewClient {
        // Handle SSL issue
        @Override
        public void onReceivedSslError(WebView view, final SslErrorHandler handler, SslError error) {
            String failingUrl = error.getUrl();
            String currentUrl = view.getUrl();
            boolean isMainPage = (currentUrl != null && failingUrl != null && failingUrl.equals(currentUrl));
            if (!isMainPage) {
                handler.cancel();
                return;
            }

            final AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            builder.setMessage(R.string.notification_error_ssl_cert_invalid);

            builder.setPositiveButton("continue", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    handler.proceed();
                }
            });

            builder.setNegativeButton("cancel", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    handler.cancel();
                }
            });
            final AlertDialog dialog = builder.create();
            dialog.show();
        }

        // Handle HTTP Basic Auth
        @Override
        public void onReceivedHttpAuthRequest(final WebView view, final android.webkit.HttpAuthHandler handler, String host, String realm) {
            // If basicAuth is set and valid, try to parse it
            if (MainActivity.this.basicAuth != null && !MainActivity.this.basicAuth.isEmpty()) {
                String[] parts = MainActivity.this.basicAuth.split(":", 2);
                if (parts.length == 2) {
                    String login = parts[0];
                    String password = parts[1];

                    // Get main domain from mainURL to verify the host
                    String mainDomain = "";
                    try {
                        mainDomain = Uri.parse(MainActivity.this.mainURL).getHost();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    boolean domainIsValid = false;
                    if (mainDomain != null && !mainDomain.isEmpty() && host != null && !host.isEmpty()) {
                        if (MainActivity.this.allowSubdomains) {
                            // Allow if the host ends with mainDomain (covers subdomains)
                            domainIsValid = host.endsWith(mainDomain) || mainDomain.endsWith(host);
                        } else {
                            domainIsValid = host.equals(mainDomain);
                        }
                    }

                    if (domainIsValid) {
                        // Credentials and domain are valid; proceed automatically
                        handler.proceed(login, password);
                        return;
                    }
                }
            }

            // Otherwise, show a custom dialog to prompt for credentials
            // Inflate a custom layout with two EditText fields for username and password
            final View dialogView = getLayoutInflater().inflate(R.layout.auth_dialog, null);
            final EditText usernameInput = dialogView.findViewById(R.id.username);
            final EditText passwordInput = dialogView.findViewById(R.id.password);

            new AlertDialog.Builder(MainActivity.this)
                .setTitle("Authentication Required")
                .setView(dialogView)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // Retrieve user input and proceed with HTTP authentication
                        String user = usernameInput.getText().toString();
                        String pass = passwordInput.getText().toString();
                        handler.proceed(user, pass);
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        handler.cancel();
                    }
                })
                .show();
        }

        // Check for external link
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();

            // Check for non-standard URL scheme (external app)
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                if (allowOpenMobileApp) {
                    if (confirmOpenExternalApp) {
                        // Show confirmation dialog before opening external app
                        new AlertDialog.Builder(view.getContext())
                            .setTitle(R.string.external_link)
                            .setMessage(R.string.open_in_external_app)
                            .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    try {
                                        // Try to launch an external Intent for the custom scheme
                                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                                        view.getContext().startActivity(intent);
                                    } catch (ActivityNotFoundException e) {
                                        Log.e("WebToApk", "\033[0;31mNo application can handle this URL:\033[0m " + url, e);
                                    }
                                }
                            })
                            .setNegativeButton(android.R.string.no, null)
                            .show();
                    } else {
                        // Open directly without confirmation
                        try {
                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                            view.getContext().startActivity(intent);
                        } catch (ActivityNotFoundException e) {
                            Log.e("WebToApk", "\033[0;31mNo application can handle this URL:\033[0m " + url);
                        }
                    }
                } else {
                    Log.d("WebToApk", "Opening URLs in external app is disabled: " + url);
                }
                return true; // Consume the event so that the WebView does not load this URL
            }

            // Check if the URL is internal by comparing the host/domain
            String urlDomain = request.getUrl().getHost();
            String mainDomain = Uri.parse(mainURL).getHost();

            // Safety check for malformed URLs
            if (urlDomain == null || mainDomain == null) {
                return handleExternalLink(url, view);
            }

            // Check if domains match (including subdomains if enabled)
            boolean isInternalLink;
            if (allowSubdomains) {
                // Allow:
                //   youtube.com -> m.youtube.com
                //   m.youtube.com -> youtube.com
                isInternalLink = urlDomain.endsWith(mainDomain) || mainDomain.endsWith(urlDomain);
            } else {
                isInternalLink = urlDomain.equals(mainDomain);
            }

            if (isInternalLink) {
                // Internal link: let the WebView load it normally
                return false;
            }

            return handleExternalLink(url, view);
        }

        private boolean handleExternalLink(String url, WebView view) {
            if (!enableExternalLinks) {
                return true; // Block external links
            }
            if (openExternalLinksInBrowser) {
                if (confirmOpenInBrowser) {
                    new AlertDialog.Builder(view.getContext())
                        .setTitle(R.string.external_link)
                        .setMessage(R.string.open_in_browser)
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Log.d("WebToApk", "\033[1;34mExternal link:\033[0m '" + url);
                                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                                view.getContext().startActivity(intent);
                            }
                        })
                        .setNegativeButton(android.R.string.no, null)
                        .show();
                    return true;
                } else {
                    Log.d("WebToApk", "\033[1;34mExternal link:\033[0m '" + url);
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    view.getContext().startActivity(intent);
                    return true;
                }
            }
            // Open in WebView
            Log.d("WebToApk", "\033[1;34mExternal link:\033[0m '" + url);
            return false;
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            String host = request.getUrl().getHost();

            if (blockLocalhostRequests && ("127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host) || "::1".equals(host) || "0:0:0:0:0:0:0:1".equals(host))) {
                Log.d("WebToApk", "Blocked access to localhost resource: " + request.getUrl().toString());
                // Empty answer
                return new WebResourceResponse("text/plain", "UTF-8", null);
            }

            return super.shouldInterceptRequest(view, request);
        }

        @Override
        public void onPageStarted(WebView webview, String url, Bitmap favicon) {
            super.onPageStarted(webview, url, favicon);
            userScriptManager.injectScripts(webview, url);
        }

        // Animation on app open
        @Override
        public void onPageFinished(WebView webview, String url) {
            // Без флага errorOccurred у нас будет видно ошибку webview пока идёт анимация после tryAgain
            if (!errorOccurred) {
                Log.d("WebToApk","Current page: " + url);
                spinner.setVisibility(View.GONE);
                if (webview.getAlpha() == 0f) {
                    webview.animate().alpha(1f).setDuration(fadeInDuration).start();
                }
            }
            super.onPageFinished(webview, url);
        }

        // Show custom error page with `tryAgain` button
        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            String errorDescription = error.getDescription().toString();
            int errorCode = error.getErrorCode();

            if (request.isForMainFrame()) {
                switch (errorCode) {
                    case ERROR_AUTHENTICATION:
                    case ERROR_BAD_URL:
                    case ERROR_CONNECT:
                    case ERROR_FAILED_SSL_HANDSHAKE:
                    case ERROR_FILE:
                    case ERROR_FILE_NOT_FOUND:
                    case ERROR_HOST_LOOKUP:
                    case ERROR_IO:
                    case ERROR_PROXY_AUTHENTICATION:
                    case ERROR_TIMEOUT:
                    case ERROR_TOO_MANY_REQUESTS:
                    case ERROR_UNKNOWN:
                    case ERROR_UNSUPPORTED_AUTH_SCHEME:
                    case ERROR_UNSUPPORTED_SCHEME:
                        Log.e("WebToApk", "Major error: " + errorCode + " - " + errorDescription + " url: " + request.getUrl());
                        errorOccurred = true;
                        errorLayout = getLayoutInflater().inflate(R.layout.error, parentLayout, false);
                        parentLayout.removeView(mainLayout);
                        parentLayout.addView(errorLayout);
                        if (showDetailsOnErrorScreen) {
                            TextView errorTextView = errorLayout.findViewById(R.id.errorText);
                            if (errorTextView != null) {
                                errorTextView.setText("Error " + errorCode + ":\n" + errorDescription + "\nURL: " + request.getUrl().toString());
                            }
                        }
                        break;
                    default:
                        Log.w("WebToApk", "Minor error: " + errorCode + " - " + errorDescription + " url: " + request.getUrl());
                        break;
                }
            } else {
                Log.d("WebToApk", "Resource error: " + errorCode + " - " + errorDescription + " url: " + request.getUrl());
            }
        }

        @Override
        public boolean onRenderProcessGone(WebView view, android.webkit.RenderProcessGoneDetail detail) {
            Log.e("WebToApk", "WebView render process gone! Did crash: " + detail.didCrash());
            if (webview != null) {
                ((ViewGroup)webview.getParent()).removeView(webview);
                webview.destroy();
                webview = null;
            }
            Toast.makeText(MainActivity.this, "Recovering from memory kill...", Toast.LENGTH_SHORT).show();
            finish();
            startActivity(getIntent());
            return true;
        }

    }

    boolean doubleBackToExitPressedOnce = false;
    volatile boolean isInForeground = false;


    /** Fire a JS callback defined on window.WebToApkCallbacks.<name>(arg). */
    private void fireJsCallback(String name, String jsonArg) {
        if (webview == null) return;
        String js = "(function(){ var cb = window.WebToApkCallbacks && window.WebToApkCallbacks['"
                + name + "']; if(typeof cb==='function') cb(" + jsonArg + "); })();";
        webview.post(() -> webview.evaluateJavascript(js, null));
    }

    /** Fire a JS callback with a full JSON object argument. */
    private void fireJsCallbackObj(String name, String jsonObj) {
        if (webview == null) return;
        String js = "(function(){ var cb = window.WebToApkCallbacks && window.WebToApkCallbacks['"
                + name + "']; if(typeof cb==='function') cb(" + jsonObj + "); })();";
        webview.post(() -> webview.evaluateJavascript(js, null));
    }

    @Override
    protected void onResume() {
        super.onResume();
        isInForeground = true;
        // Check if we were opened via a notification tap (intent payload)
        handleNotificationTapIntent(getIntent());
    }

    @Override
    protected void onPause() {
        super.onPause();
        isInForeground = false;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleNotificationTapIntent(intent);
    }

    private void handleNotificationTapIntent(Intent intent) {
        if (intent == null) return;
        String payload = intent.getStringExtra(NotificationReceiver.EXTRA_PAYLOAD);
        if (payload != null && !payload.isEmpty()) {
            try {
                org.json.JSONObject obj = new org.json.JSONObject();
                obj.put("payload", payload);
                obj.put("title", intent.getStringExtra(NotificationReceiver.EXTRA_TITLE) != null
                        ? intent.getStringExtra(NotificationReceiver.EXTRA_TITLE) : "");
                obj.put("message", intent.getStringExtra(NotificationReceiver.EXTRA_MESSAGE) != null
                        ? intent.getStringExtra(NotificationReceiver.EXTRA_MESSAGE) : "");
                fireJsCallbackObj("onNotificationTapped", obj.toString());
                // Clear so it doesn't re-fire on next onResume
                intent.removeExtra(NotificationReceiver.EXTRA_PAYLOAD);
            } catch (Exception e) {
                Log.e("WebToApk", "handleNotificationTapIntent error", e);
            }
        }
    }

    private void setupBackHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webview != null && webview.canGoBack()) {
                    webview.goBack();
                } else {
                    if (doubleBackToExitPressedOnce || !requireDoubleBackToExit) {
                        finish();
                        return;
                    }
                    doubleBackToExitPressedOnce = true;
                    Toast.makeText(MainActivity.this, R.string.exit_app, Toast.LENGTH_SHORT).show();
                    new Handler(Looper.getMainLooper()).postDelayed(() -> doubleBackToExitPressedOnce = false, 2000);
                }
            }
        });
    }

    /* Retry Loading the page */
    public void tryAgain(View v) {
        parentLayout.removeView(errorLayout);
        parentLayout.addView(mainLayout);
        webview.setAlpha(0f);
        spinner.setVisibility(View.VISIBLE);
        errorOccurred = false;
        webview.reload();
    }

    // JS API
    private class WebAppInterface {
        private Context context;

        WebAppInterface(Context context) {
            this.context = context;
        }

        // ── Toast ─────────────────────────────────────────────────────────────

        /** Show a short toast (≈2 s). Callable as: WebToApk.toast("hello") */
        @JavascriptInterface
        public void toast(String message) {
            new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show());
        }

        /** @deprecated Use toast(). Kept for backwards compatibility. */
        @JavascriptInterface
        public void showShortToast(String message) { toast(message); }

        /** Show a long toast (≈3.5 s). */
        @JavascriptInterface
        public void showLongToast(String message) {
            new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(context, message, Toast.LENGTH_LONG).show());
        }

        // ── Notification permission ───────────────────────────────────────────

        @JavascriptInterface
        public boolean hasNotificationPermission() {
            return NotificationHelper.hasPermission(context);
        }

        /**
         * Request notification permission (Android 13+).
         * Calls back JS: WebToApkCallbacks.onNotificationPermissionResult(granted: boolean)
         */
        @JavascriptInterface
        public void requestNotificationPermission() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (!NotificationHelper.hasPermission(context)) {
                        ActivityCompat.requestPermissions((Activity) context,
                                new String[]{Manifest.permission.POST_NOTIFICATIONS},
                                NOTIFICATION_PERMISSION_REQUEST_CODE);
                    } else {
                        fireJsCallback("onNotificationPermissionResult", "true");
                    }
                });
            } else {
                fireJsCallback("onNotificationPermissionResult", "true");
            }
        }

        @JavascriptInterface
        public String getNotificationPermissionState() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (NotificationHelper.hasPermission(context)) return "granted";
                if (ActivityCompat.shouldShowRequestPermissionRationale(
                        (Activity) context, Manifest.permission.POST_NOTIFICATIONS)) return "prompt";
                return "prompt"; // can't easily distinguish first-time vs permanently denied
            }
            return "granted";
        }

        // ── Show notification ─────────────────────────────────────────────────

        /**
         * Show an immediate notification.
         *
         * @param optionsJson JSON object with fields:
         *   id        (int)    — stable ID for updating/cancelling; auto-generated if 0
         *   title     (string)
         *   message   (string) — short body text
         *   bigText   (string) — expanded body text (optional)
         *   imageUrl  (string) — URL for big-picture style (optional)
         *   payload   (string) — arbitrary string passed to onNotificationTapped callback
         *   channel   (string) — "default" | "silent" | "urgent"  (default: "default")
         *   priority  (int)    — -2 to 2 (maps to NotificationCompat.PRIORITY_*)
         *   autoCancel (bool)  — dismiss on tap (default: true)
         *   actions   (array)  — [{label, payload}, …] up to 3 buttons
         *   group     (string) — group key for notification stacking
         *   silent    (bool)   — force silent channel (no sound/vibration)
         *
         * Returns the numeric notification ID used (useful if you passed id=0).
         */
        @JavascriptInterface
        public int showNotification(String optionsJson) {
            if (!NotificationHelper.hasPermission(context)) {
                Log.w("WebToApk", "showNotification: permission not granted");
                fireJsCallback("onNotificationPermissionResult", "false");
                return -1;
            }

            try {
                JSONObject opts = new JSONObject(optionsJson);
                int id = opts.optInt("id", 0);
                if (id == 0) id = (int)(System.currentTimeMillis() & 0x7FFFFFFF);

                String title    = opts.optString("title", "");
                String message  = opts.optString("message", "");
                String bigText  = opts.optString("bigText", null);
                String imageUrl = opts.optString("imageUrl", null);
                String payload  = opts.optString("payload", "");
                String group    = opts.optString("group", null);
                boolean silent  = opts.optBoolean("silent", false);
                boolean autoCancel = opts.optBoolean("autoCancel", true);
                int priority    = opts.optInt("priority", 0);
                String actionsJson = opts.has("actions") ? opts.getJSONArray("actions").toString() : null;

                String channelId;
                String channelName = opts.optString("channel", "default");
                switch (channelName) {
                    case "silent": channelId = NotificationHelper.CHANNEL_SILENT; break;
                    case "urgent": channelId = NotificationHelper.CHANNEL_URGENT; break;
                    default:       channelId = NotificationHelper.CHANNEL_DEFAULT; break;
                }

                final int finalId = id;
                NotificationHelper.show(context, finalId, title, message, payload,
                        channelId, silent, autoCancel, priority,
                        actionsJson, bigText, imageUrl, group, null);

                return finalId;
            } catch (Exception e) {
                Log.e("WebToApk", "showNotification error", e);
                return -1;
            }
        }

        /** Backwards-compatible simple overload: showNotification(title, message) */
        @JavascriptInterface
        public void showSimpleNotification(String title, String message) {
            if (!NotificationHelper.hasPermission(context)) return;
            int id = (int)(System.currentTimeMillis() & 0x7FFFFFFF);
            NotificationHelper.show(context, id, title, message, null,
                    NotificationHelper.CHANNEL_DEFAULT, false, true, 0,
                    null, null, null, null, null);
        }

        // ── Schedule notification ─────────────────────────────────────────────

        /**
         * Schedule a notification for a future time.
         *
         * @param optionsJson JSON object:
         *   id          (int)    — stable ID
         *   title       (string)
         *   message     (string)
         *   payload     (string)
         *   channel     (string)
         *   delayMs     (long)   — delay from now in milliseconds (use this OR triggerAt)
         *   triggerAt   (long)   — epoch milliseconds (absolute time)
         *
         * @return the notification ID, or -1 on error.
         */
        @JavascriptInterface
        public int scheduleNotification(String optionsJson) {
            try {
                JSONObject opts = new JSONObject(optionsJson);
                int id = opts.optInt("id", 0);
                if (id == 0) id = (int)(System.currentTimeMillis() & 0x7FFFFFFF);

                String title   = opts.optString("title", "");
                String message = opts.optString("message", "");
                String payload = opts.optString("payload", "");
                String channelName = opts.optString("channel", "default");
                String channelId;
                switch (channelName) {
                    case "silent": channelId = NotificationHelper.CHANNEL_SILENT; break;
                    case "urgent": channelId = NotificationHelper.CHANNEL_URGENT; break;
                    default:       channelId = NotificationHelper.CHANNEL_DEFAULT; break;
                }

                long triggerAt;
                if (opts.has("triggerAt")) {
                    triggerAt = opts.getLong("triggerAt");
                } else if (opts.has("delayMs")) {
                    triggerAt = System.currentTimeMillis() + opts.getLong("delayMs");
                } else {
                    Log.e("WebToApk", "scheduleNotification: must provide triggerAt or delayMs");
                    return -1;
                }

                if (triggerAt <= System.currentTimeMillis()) {
                    Log.w("WebToApk", "scheduleNotification: trigger time is in the past — showing now");
                    return showNotification(optionsJson);
                }

                final int finalId = id;
                NotificationHelper.schedule(context, finalId, triggerAt,
                        title, message, payload, channelId);

                // Persist so BootReceiver can restore it after reboot
                persistScheduled(finalId, triggerAt, title, message, payload, channelId);

                return finalId;
            } catch (Exception e) {
                Log.e("WebToApk", "scheduleNotification error", e);
                return -1;
            }
        }

        // ── Cancel notifications ──────────────────────────────────────────────

        /** Cancel a notification or scheduled alarm by ID. */
        @JavascriptInterface
        public void cancelNotification(int id) {
            NotificationHelper.cancel(context, id);
            removeFromPersisted(id);
        }

        /** Cancel all shown and scheduled notifications from this app. */
        @JavascriptInterface
        public void cancelAllNotifications() {
            NotificationHelper.cancelAll(context);
            context.getSharedPreferences("scheduled_notifications", Context.MODE_PRIVATE)
                    .edit().remove("list").apply();
        }

        // ── Vibration ─────────────────────────────────────────────────────────

        /**
         * Vibrate the device.
         * @param pattern  comma-separated ms values: "0,100,50,200" (off,on,off,on…)
         *                 or a single duration like "200" for a simple buzz.
         */
        @JavascriptInterface
        public void vibrate(String pattern) {
            new Handler(Looper.getMainLooper()).post(() -> {
                try {
                    String[] parts = pattern.split(",");
                    long[] timings = new long[parts.length];
                    for (int i = 0; i < parts.length; i++) {
                        timings[i] = Long.parseLong(parts[i].trim());
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        VibratorManager vm = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                        if (vm != null) {
                            Vibrator v = vm.getDefaultVibrator();
                            if (timings.length == 1) {
                                v.vibrate(VibrationEffect.createOneShot(timings[0], VibrationEffect.DEFAULT_AMPLITUDE));
                            } else {
                                v.vibrate(VibrationEffect.createWaveform(timings, -1));
                            }
                        }
                    } else {
                        Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
                        if (v != null) {
                            if (timings.length == 1) {
                                v.vibrate(VibrationEffect.createOneShot(timings[0], VibrationEffect.DEFAULT_AMPLITUDE));
                            } else {
                                v.vibrate(VibrationEffect.createWaveform(timings, -1));
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e("WebToApk", "vibrate() error: " + e.getMessage());
                }
            });
        }

        // ── Network ───────────────────────────────────────────────────────────

        /**
         * Returns current network type: "wifi", "cellular", "ethernet", "none".
         */
        @JavascriptInterface
        public String getNetworkStatus() {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return "none";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                android.net.Network net = cm.getActiveNetwork();
                if (net == null) return "none";
                NetworkCapabilities caps = cm.getNetworkCapabilities(net);
                if (caps == null) return "none";
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return "wifi";
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return "cellular";
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return "ethernet";
                return "other";
            } else {
                NetworkInfo info = cm.getActiveNetworkInfo();
                if (info == null || !info.isConnected()) return "none";
                int type = info.getType();
                if (type == ConnectivityManager.TYPE_WIFI) return "wifi";
                if (type == ConnectivityManager.TYPE_MOBILE) return "cellular";
                return "other";
            }
        }

        /** Returns true if the device is connected to any network. */
        @JavascriptInterface
        public boolean isOnline() {
            return !"none".equals(getNetworkStatus());
        }

        // ── App state ─────────────────────────────────────────────────────────

        /** Returns true when the app window is in the foreground and resumed. */
        @JavascriptInterface
        public boolean isAppInForeground() {
            return MainActivity.this.isInForeground;
        }

        /** Returns the current WebView URL. */
        @JavascriptInterface
        public String getCurrentUrl() {
            if (webview == null) return "";
            // Must be called on main thread — but @JavascriptInterface runs on a bg thread
            // so we just return the cached value
            return webview.getUrl() != null ? webview.getUrl() : "";
        }

        // ── Navigation ────────────────────────────────────────────────────────

        /** Navigate the WebView to a URL (must be http/https). */
        @JavascriptInterface
        public void openUrl(String url) {
            if (url == null || (!url.startsWith("http://") && !url.startsWith("https://"))) return;
            new Handler(Looper.getMainLooper()).post(() -> {
                if (webview != null) webview.loadUrl(url);
            });
        }

        /** Open a URL in the external browser. */
        @JavascriptInterface
        public void openUrlInBrowser(String url) {
            if (url == null) return;
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { context.startActivity(intent); }
            catch (Exception e) { Log.e("WebToApk", "openUrlInBrowser failed", e); }
        }

        /** Reload the current page. */
        @JavascriptInterface
        public void reload() {
            new Handler(Looper.getMainLooper()).post(() -> {
                if (webview != null) webview.reload();
            });
        }

        /** Clear WebView cache, cookies, and storage. */
        @JavascriptInterface
        public void clearAppData() {
            new Handler(Looper.getMainLooper()).post(() -> {
                if (webview != null) {
                    webview.clearCache(true);
                    webview.clearHistory();
                    android.webkit.CookieManager.getInstance().removeAllCookies(null);
                    android.webkit.WebStorage.getInstance().deleteAllData();
                    Log.d("WebToApk", "App data cleared");
                }
            });
        }

        /** @deprecated Renamed to clearAppData(). Kept for backwards compatibility. */
        @JavascriptInterface
        public void clearAppCache() { clearAppData(); }

        // ── App info ──────────────────────────────────────────────────────────

        /** Returns a JSON object: {versionName, versionCode, packageName}. */
        @JavascriptInterface
        public String getAppInfo() {
            try {
                android.content.pm.PackageInfo pi = context.getPackageManager()
                        .getPackageInfo(context.getPackageName(), 0);
                JSONObject obj = new JSONObject();
                obj.put("versionName", pi.versionName);
                obj.put("versionCode", Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                        ? pi.getLongVersionCode() : pi.versionCode);
                obj.put("packageName", context.getPackageName());
                obj.put("sdkInt", Build.VERSION.SDK_INT);
                return obj.toString();
            } catch (Exception e) {
                return "{}";
            }
        }

        // ── Share ─────────────────────────────────────────────────────────────

        @JavascriptInterface
        public void share(String title, String text, String url) {
            Log.d("WebToApk", "Share: " + title + " :: " + text + " " + url);
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            String shareBody = (text != null ? text : "")
                    + ((url != null && !url.isEmpty()) ? "\n" + url : "");
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, title);
            shareIntent.putExtra(Intent.EXTRA_TEXT, shareBody);
            context.startActivity(
                Intent.createChooser(shareIntent, title == null ? "Share" : title)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }

        // ── UnifiedPush / Web Push ────────────────────────────────────────────

        @JavascriptInterface
        public void unifiedPushSubscribe(String vapidPublicKey) {
            new Handler(Looper.getMainLooper()).post(() -> {
                Log.d("WebToApk", "JS shim triggered UnifiedPush registration.");
                MainActivity.this.registerForUnifiedPush(vapidPublicKey);
            });
        }

        @JavascriptInterface
        public void unifiedPushUnregister() {
            Log.d("WebToApk", "JS shim triggered UnifiedPush un-registration for default instance.");
            UnifiedPush.unregister(context, INSTANCE_DEFAULT);
        }

        @JavascriptInterface
        public String getUnifiedPushSubscriptionJson() {
            SharedPreferences prefs = context.getSharedPreferences("unifiedpush", Context.MODE_PRIVATE);
            String endpoint = prefs.getString("endpoint_" + INSTANCE_DEFAULT, null);
            String p256dh   = prefs.getString("p256dh_"   + INSTANCE_DEFAULT, null);
            String auth     = prefs.getString("auth_"     + INSTANCE_DEFAULT, null);
            if (endpoint == null || endpoint.isEmpty() || p256dh == null || auth == null) return "";
            try {
                JSONObject keys = new JSONObject();
                keys.put("p256dh", p256dh);
                keys.put("auth", auth);
                JSONObject sub = new JSONObject();
                sub.put("endpoint", endpoint);
                sub.put("expirationTime", JSONObject.NULL);
                sub.put("keys", keys);
                return sub.toString();
            } catch (JSONException e) {
                Log.e("WebToApk", "Failed to create subscription JSON", e);
                return "";
            }
        }

        @JavascriptInterface
        public String getNotificationPermissionState_push() {
            return getNotificationPermissionState();
        }

        // ── Media session ─────────────────────────────────────────────────────

        @JavascriptInterface
        public void updateMediaMetadata(String title, String artist, String album,
                                        @Nullable String artworkUrl) {
            Intent intent = new Intent(context, MediaPlaybackService.class);
            intent.setAction(MediaPlaybackService.ACTION_UPDATE_METADATA);
            intent.putExtra("title", title);
            intent.putExtra("artist", artist);
            intent.putExtra("album", album);
            intent.putExtra("artworkUrl", artworkUrl);
            context.startService(intent);
        }

        @JavascriptInterface
        public void updateMediaPlaybackState(String state) {
            Intent intent = new Intent(context, MediaPlaybackService.class);
            intent.setAction(MediaPlaybackService.ACTION_UPDATE_STATE);
            intent.putExtra("state", state);
            context.startService(intent);
        }

        @JavascriptInterface
        public void setMediaActionHandlers(String[] actions) {
            Intent intent = new Intent(context, MediaPlaybackService.class);
            intent.setAction(MediaPlaybackService.ACTION_SET_HANDLERS);
            intent.putExtra("actions", actions);
            context.startService(intent);
        }

        @JavascriptInterface
        public void updateMediaPositionState(double duration, double playbackRate, double position) {
            Intent intent = new Intent(context, MediaPlaybackService.class);
            intent.setAction(MediaPlaybackService.ACTION_UPDATE_POSITION);
            intent.putExtra("duration", duration);
            intent.putExtra("playbackRate", playbackRate);
            intent.putExtra("position", position);
            context.startService(intent);
        }

        // ── Internal helpers ──────────────────────────────────────────────────

        private void persistScheduled(int id, long triggerAt, String title,
                                      String message, String payload, String channelId) {
            SharedPreferences prefs = context.getSharedPreferences(
                    "scheduled_notifications", Context.MODE_PRIVATE);
            try {
                JSONArray list = new JSONArray(prefs.getString("list", "[]"));
                // Remove any existing entry with same id
                JSONArray filtered = new JSONArray();
                for (int i = 0; i < list.length(); i++) {
                    if (list.getJSONObject(i).getInt("id") != id) filtered.put(list.getJSONObject(i));
                }
                JSONObject item = new JSONObject();
                item.put("id", id);
                item.put("triggerAt", triggerAt);
                item.put("title", title);
                item.put("message", message);
                item.put("payload", payload != null ? payload : "");
                item.put("channelId", channelId);
                filtered.put(item);
                prefs.edit().putString("list", filtered.toString()).apply();
            } catch (Exception e) {
                Log.e("WebToApk", "Failed to persist scheduled notification", e);
            }
        }

        private void removeFromPersisted(int id) {
            SharedPreferences prefs = context.getSharedPreferences(
                    "scheduled_notifications", Context.MODE_PRIVATE);
            try {
                JSONArray list = new JSONArray(prefs.getString("list", "[]"));
                JSONArray filtered = new JSONArray();
                for (int i = 0; i < list.length(); i++) {
                    if (list.getJSONObject(i).getInt("id") != id) filtered.put(list.getJSONObject(i));
                }
                prefs.edit().putString("list", filtered.toString()).apply();
            } catch (Exception e) {
                Log.e("WebToApk", "Failed to remove persisted notification", e);
            }
        }
    }

}
