package com.schwab.urlshortener.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
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
@Table(name = "url_mappings")
public class UrlMapping extends AuditBaseEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Version private long version;

  @Column(name = "short_code", nullable = false, unique = true, length = 10)
  private String shortCode;

  @Column(name = "long_url", nullable = false, columnDefinition = "TEXT")
  private String longUrl;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id")
  private User user;

  @Column(name = "custom_alias", unique = true, length = 50)
  private String customAlias;

  @Column(name = "is_active", nullable = false)
  @Builder.Default
  private boolean isActive = true;

  @Column(name = "expires_at", columnDefinition = "TIMESTAMPTZ")
  private OffsetDateTime expiresAt;

  @Column(name = "click_count", nullable = false)
  @Builder.Default
  private long clickCount = 0L;

  /** PostgreSQL text[] column mapped directly through Hibernate's array JDBC type. */
  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "tags", columnDefinition = "text[]")
  @Builder.Default
  private List<String> tags = new ArrayList<>();

  public boolean isExpired() {
    return expiresAt != null && OffsetDateTime.now().isAfter(expiresAt);
  }

  public void incrementClickCount() {
    this.clickCount++;
  }
}
