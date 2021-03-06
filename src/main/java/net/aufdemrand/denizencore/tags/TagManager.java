package net.aufdemrand.denizencore.tags;

import net.aufdemrand.denizencore.DenizenCore;
import net.aufdemrand.denizencore.objects.*;
import net.aufdemrand.denizencore.scripts.ScriptEntry;
import net.aufdemrand.denizencore.tags.core.*;
import net.aufdemrand.denizencore.utilities.CoreUtilities;
import net.aufdemrand.denizencore.utilities.debugging.dB;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.*;

public class TagManager {

    public TagManager() {

    }

    public void registerCoreTags() {
        // Objects
        new ListTags();
        new QueueTags();
        new ScriptTags();

        // Utilities
        new ContextTags();
        new DefinitionTags();
        new EscapeTags();
        new ProcedureScriptTags();
        new UtilTags();
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface TagEvents {
    }

    private static List<Method> methods = new ArrayList<Method>();
    private static List<Object> method_objects = new ArrayList<Object>();

    public static HashMap<String, TagRunnable.RootForm> handlers = new HashMap<String, TagRunnable.RootForm>();

    public static void registerTagEvents(Object o) {
        for (Method method : o.getClass().getMethods()) {
            if (!method.isAnnotationPresent(TagManager.TagEvents.class)) {
                continue;
            }
            Class[] parameters = method.getParameterTypes();
            if (parameters.length != 1 || parameters[0] != ReplaceableTagEvent.class) {
                dB.echoError("Class " + o.getClass().getCanonicalName() + " has a method "
                        + method.getName() + " that is targeted at the event manager but has invalid parameters.");
                break;
            }
            registerMethod(method, o);
        }
    }

    public static void unregisterTagEvents(Object o) {
        for (int i = 0; i < methods.size(); i++) {
            if (method_objects.get(i) == o) {
                methods.remove(i);
                method_objects.remove(i);
                i--;
            }
        }
    }

    public static void registerMethod(Method method, Object o) {
        method.setAccessible(true); // Reduce invoke checks
        methods.add(method);
        method_objects.add(o);
    }

    public static void registerTagHandler(TagRunnable.RootForm run, String... names) {
        if (names.length == 1) {
            run.name = names[0];
            handlers.put(run.name, run);
        }
        else {
            for (String name : names) {
                TagRunnable.RootForm rtemp = run.clone();
                rtemp.name = name;
                handlers.put(rtemp.name, rtemp);
            }
        }
    }

    public static void fireEvent(ReplaceableTagEvent event) {
        if (dB.verbose) {
            dB.log("Tag fire: " + event.raw_tag + ", " + event.isInstant() + ", " + event.getAttributes().attributes[0].rawKey.contains("@") + ", " + event.hasAlternative() + "...");
        }
        if (event.getAttributes().attributes[0].rawKey.contains("@")) {
            fetchObject(event);
            return;
        }
        TagRunnable.RootForm handler = handlers.get(event.getName());
        if (handler != null) {
            try {
                if (dB.verbose) {
                    dB.log("Tag handle: " + event.raw_tag + " " + handler.name + "...");
                }
                handler.run(event);
                if (event.replaced()) {
                    if (dB.verbose) {
                        dB.log("Tag handle success: " + event.getReplaced());
                    }
                    return;
                }
            }
            catch (Throwable ex) {
                dB.echoError(ex);
            }
        }
        for (int i = 0; i < methods.size(); i++) {
            try {
                // TODO: non-reflection invocation option?! Maybe JIT a runnable?
                methods.get(i).invoke(method_objects.get(i), event);
                if (event.replaced()) {
                    if (dB.verbose) {
                        dB.log("Tag alt-handle success: " + methods.get(i).getName() + " on " + methods.get(i).getDeclaringClass().getCanonicalName() + " : " + event.getReplaced());
                    }
                    return;
                }
            }
            catch (Throwable ex) {
                dB.echoError(ex);
            }
        }
        if (dB.verbose) {
            dB.log("Tag unhandled!");
        }
    }

    // INTERNAL MAPPING NOTE:
    // 0x01: <
    // 0x02: >
    // 0x04: Exclusively For Utilities.talkToNPC()
    // 0x05: |
    // 0x2011: ;

    /**
     * Cleans escaped symbols generated within Tag Manager so that
     * they can be parsed now.
     *
     * @param input the potentially escaped input string.
     * @return the cleaned output string.
     */
    public static String cleanOutput(String input) {
        if (input == null) {
            return null;
        }
        char[] data = input.toCharArray();
        for (int i = 0; i < data.length; i++) {
            switch (data[i]) {
                case 0x01:
                    data[i] = '<';
                    break;
                case 0x02:
                    data[i] = '>';
                    break;
                case 0x07:
                    data[i] = '[';
                    break;
                case 0x09:
                    data[i] = ']';
                    break;
                case dList.internal_escape_char:
                    data[i] = '|';
                    break;
                default:
                    break;
            }
        }
        return new String(data);
    }

    /**
     * Cleans any potential internal escape characters (secret characters
     * used to hold the place of symbols that might get parsed weirdly
     * like > or | ) back into their proper form. Use this function
     * when outputting information that is going to be read by a
     * person.
     *
     * @param input the potentially escaped input string.
     * @return the cleaned output string.
     */
    public static String cleanOutputFully(String input) {
        if (input == null) {
            return null;
        }
        char[] data = input.toCharArray();
        for (int i = 0; i < data.length; i++) {
            switch (data[i]) {
                case 0x01:
                    data[i] = '<';
                    break;
                case 0x02:
                    data[i] = '>';
                    break;
                case 0x2011:
                    data[i] = ';';
                    break;
                case 0x07:
                    data[i] = '[';
                    break;
                case 0x09:
                    data[i] = ']';
                    break;
                case dList.internal_escape_char:
                    data[i] = '|';
                    break;
                case 0x00A0:
                    data[i] = ' ';
                    break;
                default:
                    break;
            }
        }
        return new String(data);
    }

    public static String escapeOutput(String input) {
        if (input == null) {
            return null;
        }
        char[] data = input.toCharArray();
        for (int i = 0; i < data.length; i++) {
            switch (data[i]) {
                case '<':
                    data[i] = 0x01;
                    break;
                case '>':
                    data[i] = 0x02;
                    break;
                case '[':
                    data[i] = 0x07;
                    break;
                case ']':
                    data[i] = 0x09;
                    break;
                case '|':
                    data[i] = dList.internal_escape_char;
                    break;
                default:
                    break;
            }
        }
        return new String(data);
    }

    public static void fetchObject(ReplaceableTagEvent event) {
        String object_type = CoreUtilities.toLowerCase(CoreUtilities.split(event.getAttributes().attributes[0].rawKey, '@').get(0));
        Class object_class = ObjectFetcher.getObjectClass(object_type);

        if (object_class == null) {
            dB.echoError("Invalid object type! Could not fetch '" + object_type + "'!");
            event.setReplaced("null");
            return;
        }

        dObject arg;
        try {

            if (!ObjectFetcher.checkMatch(object_class, event.hasNameContext() ? event.getAttributes().attributes[0].rawKey + '[' + event.getNameContext() + ']'
                    : event.getAttributes().attributes[0].rawKey)) {
                dB.echoDebug(event.getScriptEntry(), "Returning null. '" + event.getAttributes().attributes[0].rawKey
                        + "' is an invalid " + object_class.getSimpleName() + ".");
                event.setReplaced("null");
                return;
            }

            arg = ObjectFetcher.getObjectFrom(object_class, event.hasNameContext() ? event.getAttributes().attributes[0].rawKey + '[' + event.getNameContext() + ']'
                    : event.getAttributes().attributes[0].rawKey, DenizenCore.getImplementation().getTagContext(event.getScriptEntry()));

            if (arg == null) {
                dB.echoError(((event.hasNameContext() ? event.getAttributes().attributes[0].rawKey + '[' + event.getNameContext() + ']'
                        : event.getAttributes().attributes[0].rawKey) + " is an invalid dObject!"));
                event.setReplaced("null");
                return;
            }

            Attribute attribute = event.getAttributes();
            event.setReplacedObject(CoreUtilities.autoAttrib(arg, attribute.fulfill(1)));
        }
        catch (Exception e) {
            dB.echoError("Uh oh! Report this to the Denizen developers! Err: TagManagerObjectReflection");
            dB.echoError(e);
            event.setReplaced("null");
        }
    }

    public static void executeWithTimeLimit(final ReplaceableTagEvent event, int seconds) {

        DenizenCore.getImplementation().preTagExecute();

        ExecutorService executor = Executors.newFixedThreadPool(4);

        Future<?> future = executor.submit(new Runnable() {
            @Override
            public void run() {
                fireEvent(event);
            }
        });

        executor.shutdown();

        try {
            future.get(seconds, TimeUnit.SECONDS);
        }
        catch (InterruptedException e) {
            dB.echoError("Tag filling was interrupted!");
        }
        catch (ExecutionException e) {
            dB.echoError(e);
        }
        catch (TimeoutException e) {
            future.cancel(true);
            dB.echoError("Tag filling timed out!");
        }

        executor.shutdownNow();
    }

    public static String readSingleTag(String str, TagContext context) {
        ReplaceableTagEvent event = new ReplaceableTagEvent(str, context);
        if (event.isInstant() != context.instant) {
            return String.valueOf((char) 0x01) + str.replace('<', (char) 0x01).replace('>', (char) 0x02) + String.valueOf((char) 0x02);
        }
        return escapeOutput(readSingleTagObject(context, event).toString());
    }

    public static dObject readSingleTagObject(ParseableTagPiece tag, TagContext context) {
        if (tag.tagData.isInstant != context.instant) {
            return new Element("<" + tag.content + ">");
        }
        ReplaceableTagEvent event = new ReplaceableTagEvent(tag.tagData, tag.content, context);
        return readSingleTagObject(context, event);
    }

    public static dObject readSingleTagObject(TagContext context, ReplaceableTagEvent event) {
        // Call Event
        int tT = DenizenCore.getImplementation().getTagTimeout();
        if (dB.verbose) {
            dB.log("Tag read: " + event.raw_tag + ", " + event.isInstant() + ", " + tT + "...");
        }
        if (tT <= 0 || (!DenizenCore.getImplementation().shouldDebug(context) && !DenizenCore.getImplementation().tagTimeoutWhenSilent())) {
            fireEvent(event);
        }
        else {
            executeWithTimeLimit(event, tT);
        }
        if (!event.replaced() && event.hasAlternative()) {
            event.setReplacedObject(event.getAlternative());
        }
        if (context.debug) {
            DenizenCore.getImplementation().debugTagFill(context.entry, event.toString(), event.getReplaced());
        }
        if (!event.replaced()) {
            dB.echoError(context.entry != null ? context.entry.getResidingQueue() : null,
                    "Tag <" + event.toString() + "> is invalid!");
            return new Element(event.raw_tag);
        }
        return event.getReplacedObj();
    }

    static HashMap<String, List<ParseableTagPiece>> preCalced = new HashMap<String, List<ParseableTagPiece>>();

    public static class ParseableTagPiece {
        public String content;

        public dObject objResult = null;

        public boolean isTag = false;

        public boolean isError = false;

        public ReplaceableTagEvent.ReferenceData tagData = null;

        @Override
        public String toString() {
            return "(" + isError + ", " + isTag + ", " + (isTag ? tagData.isInstant + ", " + tagData.rawTag : "") + ", " + content + "," + objResult + ")";
        }
    }

    public static dObject parseChainObject(List<ParseableTagPiece> pieces, TagContext context, boolean repush) {
        if (dB.verbose) {
            dB.log("Tag parse chain: " + pieces + "...");
        }
        if (pieces.size() < 2) {
            if (pieces.size() == 0) {
                return new Element("");
            }
            ParseableTagPiece pzero = pieces.get(0);
            if (pzero.isTag) {
                dObject objt = readSingleTagObject(pzero, context);
                if (repush) {
                    ParseableTagPiece piece = new ParseableTagPiece();
                    piece.objResult = objt;
                    pieces.set(0, piece);
                }
                return objt;
            }
            else if (pzero.objResult != null) {
                return pzero.objResult;
            }
            return new Element(pieces.get(0).content);
        }
        StringBuilder helpy = new StringBuilder();
        for (int i = 0; i < pieces.size(); i++) {
            ParseableTagPiece p = pieces.get(i);
            if (p.isError) {
                dB.echoError(context.entry != null ? context.entry.getResidingQueue() : null, p.content);
            }
            else {
                if (p.isTag) {
                    dObject objt = readSingleTagObject(p, context);
                    if (repush) {
                        ParseableTagPiece piece = new ParseableTagPiece();
                        piece.objResult = objt;
                        pieces.set(i, piece);
                    }
                    helpy.append(objt.toString());
                }
                else if (p.objResult != null) {
                    helpy.append(p.objResult.toString());
                }
                else {
                    helpy.append(p.content);
                }
            }
        }
        return new Element(helpy.toString());
    }

    public static String tag(String arg, TagContext context) {
        return cleanOutput(tagObject(arg, context).toString());
    }

    public static List<ParseableTagPiece> genChain(String arg, ScriptEntry entry) {
        return genChain(arg, DenizenCore.getImplementation().getTagContext(entry));
    }

    public static List<ParseableTagPiece> genChain(String arg, TagContext context) {
        if (arg == null) {
            return null;
        }
        arg = cleanOutput(arg);
        List<ParseableTagPiece> pieces = preCalced.get(arg);
        if (pieces != null) {
            return pieces;
        }
        pieces = new ArrayList<ParseableTagPiece>();
        if (arg.indexOf('>') == -1 || arg.length() < 3) {
            ParseableTagPiece txt = new ParseableTagPiece();
            txt.content = arg;
            pieces.add(txt);
            return pieces;
        }
        int[] positions = new int[2];
        positions[0] = -1;
        locateTag(arg, positions);
        if (positions[0] == -1) {
            ParseableTagPiece txt = new ParseableTagPiece();
            txt.content = arg;
            pieces.add(txt);
            return pieces;
        }
        String orig = arg;
        while (positions[0] != -1) {
            ParseableTagPiece preText = null;
            if (positions[0] > 0) {
                preText = new ParseableTagPiece();
                preText.content = arg.substring(0, positions[0]);
                pieces.add(preText);
            }
            String tagToProc = arg.substring(positions[0] + 1, positions[1]);
            ParseableTagPiece midTag = new ParseableTagPiece();
            midTag.content = tagToProc;
            midTag.isTag = true;
            midTag.tagData = new ReplaceableTagEvent(tagToProc, context).mainRef;
            pieces.add(midTag);
            if (dB.verbose) {
                dB.log("Tag: " + (preText == null ? "<null>" : preText.content) + " ||| " + midTag.content);
            }
            arg = arg.substring(positions[1] + 1);
            locateTag(arg, positions);
        }
        if (arg.indexOf('<') != -1) {
            ParseableTagPiece errorNote = new ParseableTagPiece();
            errorNote.isError = true;
            errorNote.content = "Potential issue: inconsistent tag marks in command! (issue snippet: " + arg + "; from: " + orig + ")";
            pieces.add(errorNote);
        }
        if (arg.length() > 0) {
            ParseableTagPiece postText = new ParseableTagPiece();
            postText.content = arg;
            pieces.add(postText);
        }
        if (dB.verbose) {
            dB.log("Tag chainify complete: " + arg);
        }
        return pieces;
    }

    public static dObject tagObject(String arg, TagContext context) {
        return parseChainObject(genChain(arg, context), context, false);
    }

    public static int findColonNotTagNorSpace(String arg) {
        if (arg.indexOf(':') == -1) {
            return -1;
        }
        char[] arr = arg.toCharArray();
        int bracks = 0;
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] == '<') {
                bracks++;
            }
            else if (arr[i] == '>') {
                bracks--;
            }
            else if (arr[i] == ':' && bracks == 0) {
                return i;
            }
            else if (arr[i] == ' ' && bracks == 0) {
                return -1;
            }
        }
        return -1;
    }

    private static void locateTag(String arg, int[] holder) {
        int first = arg.indexOf('<');
        holder[0] = first;
        if (first == -1) {
            return;
        }
        int len = arg.length();
        // Handle "<-" for the flag command
        if (first + 1 < len && (arg.charAt(first + 1) == '-')) {
            locateTag(arg.substring(0, first) + (char) 0x01 + arg.substring(first + 1), holder);
            return;
        }
        int bracks = 1;
        for (int i = first + 1; i < len; i++) {
            if (arg.charAt(i) == '<') {
                bracks++;
            }
            else if (arg.charAt(i) == '>') {
                bracks--;
                if (bracks == 0) {
                    holder[1] = i;
                    return;
                }
            }
        }
        holder[0] = -1;
    }

    public static List<dObject> fillArgumentsObjects(List<String> args, TagContext context) {
        if (dB.verbose) {
            dB.log("Fill argument objects (old): " + args + ", " + context.instant + "...");
        }
        List<dObject> filledArgs = new ArrayList<dObject>();

        int nested_level = 0;
        if (args != null) {
            for (String argument : args) {
                // Check nested level to avoid filling tags prematurely.
                if (argument.equals("{")) {
                    nested_level++;
                }
                if (argument.equals("}")) {
                    nested_level--;
                }
                // If this argument isn't nested, fill the tag.
                if (nested_level < 1) {
                    filledArgs.add(tagObject(argument, context));
                }
                else {
                    filledArgs.add(new Element(argument));
                }
            }
        }
        return filledArgs;
    }

    public static void fillArgumentsObjects(List<dObject> args, List<String> strArgs, List<ScriptEntry.Argument> pieceHelp, List<aH.Argument> aHArgs, boolean repush, TagContext context, int[] targets) {
        if (dB.verbose) {
            dB.log("Fill argument objects: " + args + ", " + context.instant + ", " + targets.length + "...");
        }
        for (int argId : targets) {
            aH.Argument aharg = aHArgs.get(argId);
            if (aharg.needsFill || aharg.hasSpecialPrefix) {
                ScriptEntry.Argument piece = pieceHelp.get(argId);
                if (piece.prefix != null) {
                    if (piece.prefix.aHArg.needsFill) {
                        aharg.prefix = parseChainObject(piece.prefix.value, context, repush).toString();
                        aharg.lower_prefix = CoreUtilities.toLowerCase(aharg.prefix);
                    }
                    if (aharg.needsFill) {
                        aharg.object = parseChainObject(piece.value, context, repush);
                    }
                    String fullx = aharg.prefix + ":" + aharg.object.toString();
                    args.set(argId, new Element(fullx));
                    strArgs.set(argId, fullx);
                }
                else {
                    dObject created = parseChainObject(piece.value, context, repush);
                    args.set(argId, created);
                    strArgs.set(argId, created.toString());
                    aharg.object = created;
                    aharg.prefix = null;
                    aharg.lower_prefix = null;
                }
            }
        }
    }

    public static List<String> fillArguments(List<String> args, TagContext context) {
        List<String> filledArgs = new ArrayList<String>();

        int nested_level = 0;
        if (args != null) {
            for (String argument : args) {
                // Check nested level to avoid filling tags prematurely.
                if (argument.equals("{")) {
                    nested_level++;
                }
                if (argument.equals("}")) {
                    nested_level--;
                }
                // If this argument isn't nested, fill the tag.
                if (nested_level < 1) {
                    filledArgs.add(tag(argument, context));
                }
                else {
                    filledArgs.add(argument);
                }
            }
        }
        return filledArgs;
    }

    public static List<String> fillArguments(String[] args, TagContext context) {
        List<String> filledArgs = new ArrayList<String>();
        if (args != null) {
            for (String argument : args) {
                filledArgs.add(tag(argument, context));
            }
        }
        return filledArgs;
    }
}
