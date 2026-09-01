package io.opensharing.http;

import io.opensharing.config.OpenSharingProperties;
import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/**
 * Applies the protocol's pagination contract to a repository query: bounded {@code maxResults},
 * opaque {@code pageToken}, and a {@code nextPageToken} that is absent once results are exhausted.
 */
@Component
public class Listings {

  private final OpenSharingProperties.Pagination pagination;

  public Listings(OpenSharingProperties properties) {
    this.pagination = properties.getPagination();
  }

  public <E, T> ListResponse<T> page(
      Integer maxResults,
      String pageToken,
      Function<Pageable, Page<E>> query,
      Function<E, T> mapper) {
    int offset = PageTokens.offsetOf(pageToken);
    if (maxResults != null && maxResults == 0) {
      Page<E> probe = query.apply(new OffsetPageable(offset, 1));
      return ListResponse.of(List.of(), probe.hasContent() ? PageTokens.encode(offset) : null);
    }
    Page<E> page = query.apply(new OffsetPageable(offset, resolveMaxResults(maxResults)));
    return ListResponse.of(
        page.getContent().stream().map(mapper).toList(), PageTokens.nextToken(page, offset));
  }

  private int resolveMaxResults(Integer maxResults) {
    if (maxResults == null) {
      return pagination.getDefaultMaxResults();
    }
    if (maxResults < 0) {
      throw ApiException.invalidParameter("maxResults must be non-negative");
    }
    return Math.min(maxResults, pagination.getMaxMaxResults());
  }
}
