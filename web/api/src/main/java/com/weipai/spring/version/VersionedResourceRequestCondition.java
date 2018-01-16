package com.weipai.spring.version;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.condition.AbstractRequestCondition;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

import static java.util.Arrays.asList;

public class VersionedResourceRequestCondition extends AbstractRequestCondition<VersionedResourceRequestCondition> {
    private Logger logger = LoggerFactory.getLogger(VersionedResourceRequestCondition.class);
    private final Set<VersionRange> versions;
    private final Set<String> acceptedMediaTypes;
    private final boolean isDefault;

    public VersionedResourceRequestCondition(String acceptedMediaTypes[], String from, String to, boolean isDefault) {
        this(acceptedMediaTypes, versionRange(from, to), isDefault);
    }

    public VersionedResourceRequestCondition(String[] acceptedMediaTypes, Collection<VersionRange> versions, boolean isDefault) {
       this(new HashSet<>(asList(acceptedMediaTypes)), versions, isDefault);
    }

    public VersionedResourceRequestCondition(Set<String> newMediaTypes, Collection<VersionRange> versions, boolean isDefault) {
        this.acceptedMediaTypes = newMediaTypes;
        this.versions = Collections.unmodifiableSet(new HashSet<>(versions));
        this.isDefault = isDefault;
    }

    private static Set<VersionRange> versionRange(String from, String to) {
        HashSet<VersionRange> versionRanges = new HashSet<>();

        if (StringUtils.hasText(from)) {
            String toVersion = (StringUtils.hasText(to) ? to : Version.MAX_VERSION);
            VersionRange versionRange = new VersionRange(from, toVersion);

            versionRanges.add(versionRange);
        }

        return versionRanges;
    }

    @Override
    public VersionedResourceRequestCondition combine(VersionedResourceRequestCondition other) {
        logger.debug("Combining:\n{}\n{}", this, other);
        Set<VersionRange> newVersions = new LinkedHashSet<VersionRange>(this.versions);
        newVersions.addAll(other.versions);
        Set<String> newMediaTypes = new HashSet<>();
        newMediaTypes.addAll(acceptedMediaTypes);
        newMediaTypes.addAll(other.acceptedMediaTypes);
        return new VersionedResourceRequestCondition(newMediaTypes, newVersions, this.isDefault || other.isDefault);
    }

    @Override
    public VersionedResourceRequestCondition getMatchingCondition(HttpServletRequest request) {

        String accept = request.getHeader("Accept");
        if (accept != null) {
            Pattern regexPattern = Pattern.compile("(.*)-(\\d+\\.\\d+\\.\\d+).*");
            Matcher matcher = regexPattern.matcher(accept);
            if (matcher.matches()) {
                String actualMediaType = matcher.group(1);
                String version = matcher.group(2);
                logger.debug("Version={}", version);

                if (acceptedMediaTypes.contains(actualMediaType)) {

                    for (VersionRange versionRange : versions) {
                        if (versionRange.includes(version)) {
                            return this;
                        }
                    }
                }
            }
        }
        else {
            if (isDefault)
                return this;
        }

        logger.debug("Didn't find matching version");
        return null;
    }

    @Override
    public int compareTo(VersionedResourceRequestCondition other, HttpServletRequest request) {
        return 0;
    }

    @Override
    protected Collection<?> getContent() {
        return versions;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("version={");
        sb.append("media=").append(acceptedMediaTypes).append(",");
        for (VersionRange range : versions) {
            sb.append(range).append(",");
        }
        sb.append("}");

        return sb.toString();
    }

    @Override
    protected String getToStringInfix() {
        return " && ";
    }
}
