package io.github.railgun19457.proxytab.placeholder;

import java.util.Locale;
import java.util.OptionalInt;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.Context;
import net.kyori.adventure.text.minimessage.internal.parser.node.TagNode;
import net.kyori.adventure.text.minimessage.internal.parser.node.ValueNode;
import net.kyori.adventure.text.minimessage.tag.Inserting;
import net.kyori.adventure.text.minimessage.tag.Modifying;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.ArgumentQueue;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tree.Node;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public final class CustomTagResolvers {
    private static final int MAX_ALIGNMENT_WIDTH = 256;
    private static final int COLUMN_WIDTH_PIXELS = 6;
    private static final int SPACE_ADVANCE_PIXELS = 4;
    private static final int DEFAULT_ADVANCE_PIXELS = 6;
    private static final int WIDE_ADVANCE_PIXELS = 10;
    private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();
    private static final TagResolver ALL = TagResolver.resolver(
        TagResolver.resolver("left", (args, context) -> alignmentTag(Alignment.LEFT, args, context)),
        TagResolver.resolver("right", (args, context) -> alignmentTag(Alignment.RIGHT, args, context)),
        TagResolver.resolver("center", (args, context) -> alignmentTag(Alignment.CENTER, args, context)),
        TagResolver.resolver("align", CustomTagResolvers::alignTag)
    );

    private CustomTagResolvers() {
    }

    public static TagResolver all() {
        return ALL;
    }

    private static Tag alignTag(ArgumentQueue args, Context context) {
        String rawAlignment = args.popOr("align tag requires an alignment mode.").lowerValue();
        Alignment alignment = Alignment.parse(rawAlignment);
        if (alignment == null) {
            throw context.newException("Unknown alignment mode: " + rawAlignment, args);
        }

        return alignmentTag(alignment, args, context);
    }

    private static Tag alignmentTag(Alignment alignment, ArgumentQueue args, Context context) {
        int width = readWidth(args, context);
        return new AlignmentTag(alignment, width);
    }

    private static int readWidth(ArgumentQueue args, Context context) {
        OptionalInt parsed = args.popOr("alignment tag requires a width.").asInt();
        if (parsed.isEmpty()) {
            throw context.newException("Alignment width must be an integer.", args);
        }

        int width = parsed.getAsInt();
        if (width < 0 || width > MAX_ALIGNMENT_WIDTH) {
            throw context.newException("Alignment width must be between 0 and " + MAX_ALIGNMENT_WIDTH + ".", args);
        }
        return width;
    }

    private static final class AlignmentTag implements Modifying {
        private final Alignment alignment;
        private final int width;
        private int contentWidthPixels;
        private int visibleLeaves;
        private int visitedLeaves;
        private String prefix = "";
        private String suffix = "";

        private AlignmentTag(Alignment alignment, int width) {
            this.alignment = alignment;
            this.width = width;
        }

        @Override
        public void visit(Node current, int depth) {
            int leafWidth = leafWidth(current);
            if (leafWidth <= 0) {
                return;
            }

            contentWidthPixels += leafWidth;
            visibleLeaves++;
        }

        @Override
        public void postVisit() {
            int targetWidthPixels = width * COLUMN_WIDTH_PIXELS;
            int missingPixels = targetWidthPixels - contentWidthPixels;
            if (missingPixels <= 0) {
                return;
            }

            int padding = (missingPixels + SPACE_ADVANCE_PIXELS - 1) / SPACE_ADVANCE_PIXELS;

            switch (alignment) {
                case LEFT -> suffix = " ".repeat(padding);
                case RIGHT -> prefix = " ".repeat(padding);
                case CENTER -> {
                    int leftPadding = padding / 2;
                    prefix = " ".repeat(leftPadding);
                    suffix = " ".repeat(padding - leftPadding);
                }
            }
        }

        @Override
        public Component apply(Component current, int depth) {
            Component base = current.children(java.util.List.of());
            if (visibleLeaves == 0) {
                return depth == 0 ? Component.text(prefix + suffix) : base;
            }

            if (displayWidth(PLAIN_TEXT.serialize(base)) <= 0) {
                return base;
            }

            visitedLeaves++;
            if (!prefix.isEmpty() && visitedLeaves == 1) {
                base = Component.text(prefix).append(base);
            }
            if (!suffix.isEmpty() && visitedLeaves == visibleLeaves) {
                base = base.append(Component.text(suffix));
            }
            return base;
        }

        private int leafWidth(Node current) {
            if (current instanceof ValueNode valueNode) {
                return displayWidth(valueNode.value());
            }
            if (current instanceof TagNode tagNode && tagNode.tag() instanceof Inserting inserting) {
                return displayWidth(PLAIN_TEXT.serialize(inserting.value()));
            }
            return 0;
        }
    }

    private static int displayWidth(String value) {
        int width = 0;
        for (int index = 0; index < value.length();) {
            int codePoint = value.codePointAt(index);
            index += Character.charCount(codePoint);
            if (!Character.isISOControl(codePoint)) {
                width += glyphAdvance(codePoint);
            }
        }
        return width;
    }

    private static int glyphAdvance(int codePoint) {
        if (codePoint == ' ') {
            return SPACE_ADVANCE_PIXELS;
        }

        if (codePoint >= '0' && codePoint <= '9') {
            return DEFAULT_ADVANCE_PIXELS;
        }

        return switch (codePoint) {
            case '!', ',', '.', ':', ';', 'i', '|' -> 2;
            case '\'', 'l' -> 3;
            case '(', ')', '<', '>', 'f', 'k', 't', '[', ']', '{', '}' -> 5;
            case '"', '*', '/', '\\' -> 5;
            case '@' -> 7;
            default -> isWide(codePoint) ? WIDE_ADVANCE_PIXELS : DEFAULT_ADVANCE_PIXELS;
        };
    }

    private static boolean isWide(int codePoint) {
        return (codePoint >= 0x1100 && codePoint <= 0x115F)
            || (codePoint >= 0x2E80 && codePoint <= 0xA4CF)
            || (codePoint >= 0xAC00 && codePoint <= 0xD7A3)
            || (codePoint >= 0xF900 && codePoint <= 0xFAFF)
            || (codePoint >= 0xFE10 && codePoint <= 0xFE19)
            || (codePoint >= 0xFE30 && codePoint <= 0xFE6F)
            || (codePoint >= 0xFF00 && codePoint <= 0xFF60)
            || (codePoint >= 0xFFE0 && codePoint <= 0xFFE6);
    }

    private enum Alignment {
        LEFT,
        RIGHT,
        CENTER;

        private static Alignment parse(String value) {
            return switch (value.toLowerCase(Locale.ROOT)) {
                case "left" -> LEFT;
                case "right" -> RIGHT;
                case "center" -> CENTER;
                default -> null;
            };
        }
    }
}
