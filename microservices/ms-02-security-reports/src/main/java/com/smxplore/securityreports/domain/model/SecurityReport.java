package com.smxplore.securityreports.domain.model;

import com.smxplore.securityreports.domain.exceptions.InvalidReportRequestException;
import com.smxplore.securityreports.domain.exceptions.InvalidStatusTransitionException;
import com.smxplore.securityreports.domain.exceptions.InvalidUserReporterException;
import com.smxplore.securityreports.domain.exceptions.ReportWithoutContentException;
import com.smxplore.securityreports.domain.model.types.Location;
import com.smxplore.securityreports.domain.model.types.UserRef;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Getter
@Builder(toBuilder = true)
public class SecurityReport {
    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_DESCRIPTION_LENGTH = 2000;
    private static final int MAX_PHOTO_URL_LENGTH = 2048;
    private static final int MAX_PHOTOS = 10;

    @Builder.Default
    private final UUID id = UUID.randomUUID();
    private UserRef user;
    private String title;
    private String description;
    private final Location location;

    @Builder.Default
    private final List<String> photosUrls = new ArrayList<>();

    @Builder.Default
    private SecurityReportStatus status = SecurityReportStatus.ONGOING;

    @Builder.Default
    private final Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private Long version;

    //Methods
    public void validate() {
        validateTitle(title);
        validateDescription(description);
        if (createdAt == null) throw new InvalidReportRequestException("Creation date is mandatory.");
        if (location != null) location.validate();
        if (user == null) throw new InvalidUserReporterException("User is empty");

        user.validate();
    }

    public void changeContent(String title, String description) {
        validateTitle(title);
        validateDescription(description);
        this.title = title;
        this.description = description;
    }

    public void addPhotos(Collection<String> photos) {
        if (photos == null) return;
        photos.forEach(this::addPhoto);
    }

    public void addPhoto(String photoUrl) {
        if (photoUrl == null || photoUrl.isBlank()) return;
        var url = validateUrl(photoUrl);

        if (photosUrls.contains(url)) return;
        if (photosUrls.size() >= MAX_PHOTOS) {
            throw new InvalidReportRequestException("A report cannot have more than " + MAX_PHOTOS + " photos.");
        }

        photosUrls.add(url);
    }

    private String validateUrl(String url){
        url = url.trim();
        if (url.length() > MAX_PHOTO_URL_LENGTH) {
            throw new InvalidReportRequestException("Photo URL is too long.");
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new InvalidReportRequestException("Photo URL must use http or https.");
        }
        return url;
    }

    public boolean removePhoto(String photo) {
        return photosUrls.remove(photo);
    }

    public boolean isOngoing() {
        return SecurityReportStatus.ONGOING.equals(status);
    }

    public boolean isReported() {
        return SecurityReportStatus.REPORTED.equals(status);
    }

    public boolean isSolved() {
        return SecurityReportStatus.SOLVED.equals(status);
    }

    public void markAsSolved() {
        if (isSolved()) throw new InvalidStatusTransitionException("Already solved");
        if (!isReported()) {
            throw new InvalidStatusTransitionException("SecurityReport must be reported before being solved");
        }
        status = SecurityReportStatus.SOLVED;
    }

    public List<String> getPhotosUrls() {
        return Collections.unmodifiableList(photosUrls);
    }

    public void markAsReported() {
        if (!isOngoing()) throw new InvalidStatusTransitionException("SecurityReport is not ongoing");
        status = SecurityReportStatus.REPORTED;
    }

    private void validateTitle(String title) {
        if (title == null || title.isBlank())
            throw new ReportWithoutContentException("Title is empty");
        if (title.length() > MAX_TITLE_LENGTH)
            throw new ReportWithoutContentException("Title is too long");
    }

    private void validateDescription(String description) {
        if (description != null && description.length() > MAX_DESCRIPTION_LENGTH)
            throw new ReportWithoutContentException("Description is too long");
    }
}
