package com.hashmap.haf.metadata.config.page;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.ToString;

import java.io.Serializable;
import java.util.Arrays;
import java.util.UUID;

@ToString
public class TextPageLink extends BasePageLink implements Serializable {

    private static final long serialVersionUID = -4550201987329812081L;

    @Getter
    private final String textSearch;
    @Getter private final String textSearchBound;
    @Getter private final String textOffset;

    public TextPageLink(int limit) {
        this(limit, null, null, null);
    }

    public TextPageLink(int limit, String textSearch) {
        this(limit, textSearch, null, null);
    }

    public TextPageLink(int limit, UUID idOffset) {
        this(limit, null, idOffset, null);
    }

    public TextPageLink(int limit, String textSearch, UUID idOffset, String textOffset) {
        super(limit, idOffset);
        this.textSearch = textSearch != null ? textSearch.toLowerCase() : null;
        this.textSearchBound = nextSequence(this.textSearch);
        this.textOffset = textOffset != null ? textOffset.toLowerCase() : null;
    }

    @JsonCreator
    public TextPageLink(@JsonProperty("limit") int limit,
                        @JsonProperty("textSearch") String textSearch,
                        @JsonProperty("textSearchBound") String textSearchBound,
                        @JsonProperty("textOffset") String textOffset,
                        @JsonProperty("idOffset") UUID idOffset) {
        super(limit, idOffset);
        this.textSearch = textSearch;
        this.textSearchBound = textSearchBound;
        this.textOffset = textOffset;
        this.idOffset = idOffset;
    }

    private static String nextSequence(String input) {
        if (input != null && input.length() > 0) {
            char[] chars = input.toCharArray();
            int i = chars.length - 1;
            while (i >= 0 && ++chars[i--] == Character.MIN_VALUE) ;
            if (i == -1 && (chars.length == 0 || chars[0] == Character.MIN_VALUE)) {
                char[] buf = Arrays.copyOf(input.toCharArray(), input.length() + 1);
                buf[buf.length - 1] = Character.MIN_VALUE;
                return new String(buf);
            }
            return new String(chars);
        } else {
            return null;
        }
    }

}
