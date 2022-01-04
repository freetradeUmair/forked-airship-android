/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.ui;

import android.os.Bundle;

import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.android.layout.R;
import com.urbanairship.android.layout.ThomasListener;
import com.urbanairship.android.layout.display.DisplayArgs;
import com.urbanairship.android.layout.display.DisplayArgsLoader;
import com.urbanairship.android.layout.environment.Environment;
import com.urbanairship.android.layout.environment.ViewEnvironment;
import com.urbanairship.android.layout.event.ButtonEvent;
import com.urbanairship.android.layout.event.Event;
import com.urbanairship.android.layout.event.EventListener;
import com.urbanairship.android.layout.event.EventSource;
import com.urbanairship.android.layout.event.ReportingEvent;
import com.urbanairship.android.layout.model.BaseModel;
import com.urbanairship.android.layout.model.ModalPresentation;
import com.urbanairship.android.layout.property.ModalPlacement;
import com.urbanairship.android.layout.reporting.AttributeName;
import com.urbanairship.android.layout.reporting.DisplayTimer;
import com.urbanairship.android.layout.util.ActionsRunner;
import com.urbanairship.android.layout.view.ModalView;
import com.urbanairship.channel.AttributeEditor;
import com.urbanairship.json.JsonValue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.view.WindowCompat;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static com.urbanairship.android.layout.event.ReportingEvent.ReportType.FORM_RESULT;

/**
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class ModalActivity extends AppCompatActivity implements EventListener, EventSource {
    // Asset loader
    public static final String EXTRA_DISPLAY_ARGS_LOADER = "com.urbanairship.android.layout.ui.EXTRA_DISPLAY_ARGS_LOADER";

    private static final String KEY_DISPLAY_TIME = "display_time";

    private final List<EventListener> listeners = new CopyOnWriteArrayList<>();

    @Nullable
    private DisplayArgsLoader loader;

    @Nullable
    private ThomasListener externalListener;

    @Nullable
    private ActionsRunner actionsRunner;

    private DisplayTimer displayTimer;
    private boolean disableBackButton = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        this.loader = getIntent().getParcelableExtra(EXTRA_DISPLAY_ARGS_LOADER);
        if (this.loader == null) {
            Logger.error("Missing layout args loader");
            finish();
            return;
        }

        try {
            DisplayArgs args = this.loader.getDisplayArgs();
            if (!(args.getPayload().getPresentation() instanceof ModalPresentation)) {
                Logger.error("Not a modal presentation");
                finish();
                return;
            }

            this.externalListener = args.getListener();
            this.actionsRunner = args.getActionsRunner();

            ModalPresentation presentation = (ModalPresentation) args.getPayload().getPresentation();

            long restoredTime = savedInstanceState != null ? savedInstanceState.getLong(KEY_DISPLAY_TIME) : 0;
            this.displayTimer = new DisplayTimer(this, restoredTime);

            ModalPlacement placement = presentation.getResolvedPlacement(this);
            if (placement.shouldIgnoreSafeArea()) {
                WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
                getWindow().setStatusBarColor(R.color.system_bar_scrim_dark);
                getWindow().setNavigationBarColor(R.color.system_bar_scrim_dark);
            }

            Environment environment = new ViewEnvironment(
                this,
                args.getWebViewClientFactory(),
                args.getImageCache(),
                displayTimer,
                placement.shouldIgnoreSafeArea()
            );

            BaseModel view = args.getPayload().getView();
            view.addListener(this);

            // Add thomas listener last so its the last thing to receive events
            if (this.externalListener != null) {
                addListener(new ThomasListenerProxy(this.externalListener));
            }

            ModalView modalView = ModalView.create(this, view, presentation, environment);
            modalView.setLayoutParams(new ConstraintLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));

            if (presentation.isDismissOnTouchOutside()) {
                modalView.setOnClickOutsideListener(v -> {
                    onEvent(new ReportingEvent.DismissFromOutside(displayTimer.getTime()));
                    finish();
                });
            }

            disableBackButton = presentation.isDisableBackButton();

            setContentView(modalView);
        } catch (@NonNull DisplayArgsLoader.LoadException e) {
            Logger.error("Failed to load model!", e);
            finish();
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (loader != null && isFinishing()) {
            loader.dispose();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(KEY_DISPLAY_TIME, displayTimer.getTime());
    }

    @Override
    public void onBackPressed() {
        if (!disableBackButton) {
            super.onBackPressed();
            onEvent(new ReportingEvent.DismissFromOutside(displayTimer.getTime()));
        }
    }

    @Override
    public boolean onEvent(@NonNull Event event) {
        Logger.verbose("onEvent: %s", event);
        switch (event.getType()) {
            case BUTTON_BEHAVIOR_CANCEL:
            case BUTTON_BEHAVIOR_DISMISS:
                reportDismissFromButton((ButtonEvent) event);
                finish();
                return true;

            case BUTTON_ACTIONS:
                return runButtonActions((ButtonEvent.Actions) event);

            case REPORTING_EVENT:
                if (((ReportingEvent) event).getReportType() == FORM_RESULT) {
                    applyAttributeUpdates((ReportingEvent.FormResult) event);
                }
                break;
        }

        for (EventListener listener : listeners) {
            if (listener.onEvent(event)) {
                return true;
            }
        }
        return false;
    }

    private boolean runButtonActions(ButtonEvent.Actions event) {
        if (actionsRunner != null) {
            actionsRunner.run(event.getActions());
            return true;
        }
        return false;
    }

    @Override
    public void addListener(EventListener listener) {
        this.listeners.add(listener);
    }

    @Override
    public void removeListener(EventListener listener) {
        this.listeners.remove(listener);
    }

    private void reportDismissFromButton(ButtonEvent event) {
        // Re-wrap the event as a reporting event and run it back through so we'll notify the external listener.
        onEvent(new ReportingEvent.DismissFromButton(
            event.getIdentifier(),
            event.getReportingDescription(),
            event.isCancel(),
            displayTimer.getTime(),
            event.getState()
        ));
    }

    private void applyAttributeUpdates(ReportingEvent.FormResult result) {
        AttributeEditor contactEditor = UAirship.shared().getContact().editAttributes();
        AttributeEditor channelEditor = UAirship.shared().getChannel().editAttributes();

        for (Map.Entry<AttributeName, JsonValue> entry : result.getAttributes().entrySet()) {
            AttributeName key = entry.getKey();
            String attribute = key.isContact() ? key.getContact() : key.getChannel();
            JsonValue value = entry.getValue();
            if (attribute == null || value == null || value.isNull()) {
                continue;
            }

            Logger.debug("Setting %s attribute: \"%s\" => %s",
                key.isChannel() ? "channel" : "contact", attribute, value.toString());

            AttributeEditor editor = key.isContact() ? contactEditor : channelEditor;
            setAttribute(editor, attribute, value);
        }

        contactEditor.apply();
        channelEditor.apply();
    }

    private void setAttribute(@NonNull AttributeEditor editor, @NonNull String attribute, @NonNull JsonValue value) {
        if (value.isString()) {
            editor.setAttribute(attribute, value.optString());
        } else if (value.isDouble()) {
            editor.setAttribute(attribute, value.getDouble(-1));
        } else if (value.isFloat()) {
            editor.setAttribute(attribute, value.getFloat(-1));
        } else if (value.isInteger()) {
            editor.setAttribute(attribute, value.getInt(-1));
        } else if (value.isLong()) {
            editor.setAttribute(attribute, value.getLong(-1));
        }
    }
}
