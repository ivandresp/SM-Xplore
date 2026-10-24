package com.smxplore.securityreports.domain.model;

import com.smxplore.securityreports.domain.exceptions.InvalidStatusTransitionException;
import com.smxplore.securityreports.domain.exceptions.InvalidUserReporterException;
import com.smxplore.securityreports.domain.exceptions.ReportWithoutContentException;
import com.smxplore.securityreports.domain.model.types.Location;
import com.smxplore.securityreports.domain.model.types.UserRef;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.*;

@Getter
@Builder(toBuilder = true)
public class SecurityReport {
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
    private final Instant createdAt = Instant.now();

    //Methods
    public void validate() {
        validateTitle(title);
        if (location != null) location.validate();
        if (user == null) throw new InvalidUserReporterException("User is empty");

        user.validate();
    }

    public void changeContent(String title, String description) {
        validateTitle(title);
        this.title = title;
        this.description = description;
    }

    public void addPhotos(Collection<String> photos) {
        if (photos == null) return;
        photos.forEach(this::addPhoto);
    }

    public void addPhoto(String photoUrl) {
        if (photoUrl == null || photoUrl.isBlank()) return;
        var url = photoUrl.trim();
        if (photosUrls.contains(url)) return;

        photosUrls.add(url);
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
    }
}
