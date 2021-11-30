/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model;

import com.urbanairship.android.layout.event.Event;
import com.urbanairship.android.layout.property.Border;
import com.urbanairship.android.layout.property.ButtonClickBehaviorType;
import com.urbanairship.android.layout.property.ButtonEnableBehaviorType;
import com.urbanairship.android.layout.property.Image;
import com.urbanairship.android.layout.property.Color;
import com.urbanairship.android.layout.property.ViewType;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonMap;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ImageButtonModel extends ButtonModel {
    @NonNull
    private final Image image;

    public ImageButtonModel(
        @NonNull String id,
        @NonNull Image image,
        @NonNull List<ButtonClickBehaviorType> clickBehaviors,
        @NonNull List<JsonMap> actions,
        @NonNull List<ButtonEnableBehaviorType> enableBehaviors,
        @Nullable Color backgroundColor,
        @Nullable Border border,
        @Nullable String contentDescription
    ) {
        super(ViewType.IMAGE_BUTTON, id, clickBehaviors, actions, enableBehaviors, backgroundColor, border, contentDescription);

        this.image = image;
    }

    @NonNull
    public static ImageButtonModel fromJson(@NonNull JsonMap json) throws JsonException {
        String id = Identifiable.identifierFromJson(json);
        JsonMap imageJson = json.opt("image").optMap();
        Image image = Image.fromJson(imageJson);
        List<ButtonClickBehaviorType> clickBehaviors = buttonClickBehaviorsFromJson(json);
        List<JsonMap> actions = actionsFromJson(json);
        List<ButtonEnableBehaviorType> enableBehaviors = buttonEnableBehaviorsFromJson(json);
        Color backgroundColor = backgroundColorFromJson(json);
        Border border = borderFromJson(json);
        String contentDescription = Accessible.contentDescriptionFromJson(json);

        return new ImageButtonModel(
            id,
            image,
            clickBehaviors,
            actions,
            enableBehaviors,
            backgroundColor,
            border,
            contentDescription
        );
    }

    //
    // Fields
    //

    @NonNull
    public Image getImage() {
        return image;
    }

    //
    // View Actions
    //

    public void onClick() {
        bubbleEvent(new Event.ButtonClick(this));
    }
}
