package com.schwab.urlshortener.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "url_analytics")
public class UrlAnalytics {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "short_code", nullable = false, length = 10)
  private String shortCode;

  @Column(name = "click_timestamp", nullable = false, columnDefinition = "TIMESTAMPTZ")
  @Builder.Default
  private OffsetDateTime clickTimestamp = OffsetDateTime.now();

  @Column(name = "click_date", nullable = false)
  private LocalDate clickDate;

  @Column(name = "ip_address", length = 45)
  private String ipAddress;

  @Column(name = "user_agent", columnDefinition = "TEXT")
  private String userAgent;

  @Column(name = "referrer", columnDefinition = "TEXT")
  private String referrer;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "country_code", length = 2, columnDefinition = "CHAR(2)")
  private String countryCode;

  @Column(name = "device_type", length = 20)
  private String deviceType;

  @Column(name = "browser", length = 50)
  private String browser;

  @Column(name = "os", length = 50)
  private String os;
}
