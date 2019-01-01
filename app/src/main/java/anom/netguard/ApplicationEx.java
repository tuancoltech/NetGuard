package anom.netguard;

/*
    This file is part of NetGuard.

    NetGuard is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    NetGuard is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with NetGuard.  If not, see <http://www.gnu.org/licenses/>.

    Copyright 2015-2018 by Marcel Bokhorst (M66B)
*/

import android.annotation.TargetApi;
import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

public class ApplicationEx extends Application {

    private static final String TAG = "NetGuard.App";

    private Thread.UncaughtExceptionHandler mPrevHandler;
    private ExecutorService mExecutorPreloadSettings = Executors.newSingleThreadExecutor();

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "Create version=" + Util.getSelfVersionName(this) + "/" + Util.getSelfVersionCode(this));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            createNotificationChannels();

        mPrevHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread thread, Throwable ex) {
                if (Util.ownFault(ApplicationEx.this, ex)
                        && Util.isPlayStoreInstall(ApplicationEx.this)) {
                    Log.e(TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
                    mPrevHandler.uncaughtException(thread, ex);
                } else {
                    Log.w(TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
                    System.exit(1);
                }
            }
        });

        mExecutorPreloadSettings.execute(new Runnable() {
            @Override
            public void run() {
                preloadConfigurationFile();
            }
        });
    }

    /**
     * Import configurations from xml file, which is stored in assets folder
     */
    private void preloadConfigurationFile() {
        InputStream in = null;
        try {
            xmlImport(getAssets().open("netguard_20181221.xml"));
        } catch (Throwable ex) {
            Log.e(TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
        } finally {
            if (in != null)
                try {
                    in.close();
                } catch (IOException ex) {
                    Log.e(TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
                }
        }
    }

    private void xmlImport(InputStream in) throws IOException, SAXException, ParserConfigurationException {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.edit().putBoolean("enabled", false).apply();
        ServiceSinkhole.stop("import", this, false);

        XMLReader reader = SAXParserFactory.newInstance().newSAXParser().getXMLReader();
        XmlImportHandler handler = new XmlImportHandler(this);
        reader.setContentHandler(handler);
        reader.parse(new InputSource(in));

        Util.xmlImport(handler.application, prefs);
        Util.xmlImport(handler.wifi, getSharedPreferences("wifi", Context.MODE_PRIVATE));
        Util.xmlImport(handler.mobile, getSharedPreferences("other", Context.MODE_PRIVATE));
        Util.xmlImport(handler.screen_wifi, getSharedPreferences("screen_wifi", Context.MODE_PRIVATE));
        Util.xmlImport(handler.screen_other, getSharedPreferences("screen_other", Context.MODE_PRIVATE));
        Util.xmlImport(handler.roaming, getSharedPreferences("roaming", Context.MODE_PRIVATE));
        Util.xmlImport(handler.lockdown, getSharedPreferences("lockdown", Context.MODE_PRIVATE));
        Util.xmlImport(handler.apply, getSharedPreferences("apply", Context.MODE_PRIVATE));
        Util.xmlImport(handler.notify, getSharedPreferences("notify", Context.MODE_PRIVATE));

        // Upgrade imported settings
        ReceiverAutostart.upgrade(true, this);

        DatabaseHelper.clearCache();

        // Refresh UI
        prefs.edit().putBoolean("imported", true).apply();
    }

    @TargetApi(Build.VERSION_CODES.O)
    private void createNotificationChannels() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        NotificationChannel foreground = new NotificationChannel("foreground", getString(R.string.channel_foreground)
                , NotificationManager.IMPORTANCE_MIN);
        foreground.setSound(null, Notification.AUDIO_ATTRIBUTES_DEFAULT);
        nm.createNotificationChannel(foreground);

        NotificationChannel notify = new NotificationChannel("notify", getString(R.string.channel_notify),
                NotificationManager.IMPORTANCE_DEFAULT);
        notify.setSound(null, Notification.AUDIO_ATTRIBUTES_DEFAULT);
        nm.createNotificationChannel(notify);

        NotificationChannel access = new NotificationChannel("access", getString(R.string.channel_access),
                NotificationManager.IMPORTANCE_DEFAULT);
        access.setSound(null, Notification.AUDIO_ATTRIBUTES_DEFAULT);
        nm.createNotificationChannel(access);
    }
}
