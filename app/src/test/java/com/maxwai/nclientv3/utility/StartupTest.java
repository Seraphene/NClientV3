package com.maxwai.nclientv3.utility;

import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import com.maxwai.nclientv3.*;
import com.maxwai.nclientv3.components.activities.CrashApplication;
import com.maxwai.nclientv3.settings.Global;
import okhttp3.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

/** Exercise the real application initialization, not a replacement Application fixture. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 31, 34}, application = StartupTest.TestApplication.class)
public class StartupTest {
    public static class TestApplication extends CrashApplication {
        @Override public void onCreate() {
            // Robolectric doesn't run the merged manifest's AndroidX startup provider.
            androidx.work.WorkManager.initialize(this, new androidx.work.Configuration.Builder().build());
            super.onCreate();
        }
    }
    @Test public void launcherAndExploreStartWithRealApplication() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        assertTrue(context instanceof CrashApplication);
        ReflectionHelpers.setStaticField(MainActivity.class, "firstTime", false);
        ReflectionHelpers.setStaticField(Global.class, "client", new OkHttpClient.Builder()
            .addInterceptor(chain -> new Response.Builder().request(chain.request())
                .protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(ResponseBody.create("{\"result\":[],\"num_pages\":1,\"per_page\":25}", MediaType.get("application/json"))).build()).build());
        ReadingSource.remember(context, false);
        try (ActivityController<PINActivity> launcher = Robolectric.buildActivity(PINActivity.class).setup()) {
            Intent intent = Shadows.shadowOf(launcher.get()).getNextStartedActivity();
            assertEquals(MainActivity.class.getName(), intent.getComponent().getClassName());
        }
        try (ActivityController<MainActivity> main = Robolectric.buildActivity(MainActivity.class).setup().visible()) {
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            assertNotNull(main.get().findViewById(R.id.recycler));
            assertTrue(main.get().isRootBrowse());
            androidx.appcompat.widget.Toolbar toolbar = main.get().findViewById(R.id.toolbar);
            main.get().onPrepareOptionsMenu(toolbar.getMenu());
            assertNotNull(toolbar.getMenu().findItem(R.id.reading_source));
        }
    }
}
