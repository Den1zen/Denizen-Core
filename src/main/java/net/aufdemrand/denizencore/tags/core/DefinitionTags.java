package net.aufdemrand.denizencore.tags.core;

import net.aufdemrand.denizencore.objects.Element;
import net.aufdemrand.denizencore.objects.TagRunnable;
import net.aufdemrand.denizencore.objects.dObject;
import net.aufdemrand.denizencore.tags.Attribute;
import net.aufdemrand.denizencore.tags.ReplaceableTagEvent;
import net.aufdemrand.denizencore.tags.TagManager;
import net.aufdemrand.denizencore.utilities.CoreUtilities;
import net.aufdemrand.denizencore.utilities.DefinitionProvider;
import net.aufdemrand.denizencore.utilities.debugging.dB;

public class DefinitionTags {

    public DefinitionTags() {
        TagManager.registerTagHandler(new TagRunnable.RootForm() {
            @Override
            public void run(ReplaceableTagEvent event) {
                definitionTag(event);
            }
        }, "definition", "def", "d");
    }


    //////////
    //  ReplaceableTagEvent handler
    ////////

    public void definitionTag(ReplaceableTagEvent event) {

        if (!event.matches("definition", "def", "d")) {
            return;
        }

        if (!event.hasNameContext()) {
            dB.echoError("Invalid definition tag, no context specified!");
            return;
        }

        // <--[tag]
        // @attribute <definition[<name>]>
        // @returns dObject
        // @description
        // Returns a definition from the current queue.
        // The object will be returned as the most-valid type based on the input.
        // -->
        // Get the definition from the name input
        String defName = event.getNameContext();

        DefinitionProvider definitionProvider = event.getContext().definitionProvider;
        if (definitionProvider == null) {
            dB.echoError("No definitions are provided at this moment!");
            return;
        }
        dObject def = definitionProvider.getDefinitionObject(defName);

        Attribute atttribute = event.getAttributes().fulfill(1);

        // <--[tag]
        // @attribute <definition[<name>].exists>
        // @returns Element(Boolean)
        // @description
        // Returns whether a definition exists for the given definition name.
        // -->
        if (atttribute.startsWith("exists")) {
            if (def == null) {
                event.setReplacedObject(CoreUtilities.autoAttrib(new Element(false), atttribute.fulfill(1)));
            }
            else {
                event.setReplacedObject(CoreUtilities.autoAttrib(new Element(true), atttribute.fulfill(1)));
            }
            return;
        }

        // No invalid definitions!
        if (def == null) {
            if (!event.hasAlternative()) {
                dB.echoError("Invalid definition name '" + defName + "'.");
            }
            return;
        }

        event.setReplacedObject(CoreUtilities.autoAttribTyped(def, atttribute));
    }
}


