package de.rechnio.service;

import java.util.List;
import java.util.UUID;

import de.rechnio.config.CurrentUser;
import de.rechnio.dto.ProductRequest;
import de.rechnio.dto.ProductResponse;

/** Artikel und Leistungen als Vorlagen fuer Rechnungspositionen. */
public interface ProductService {

    List<ProductResponse> list(CurrentUser user);

    ProductResponse get(CurrentUser user, UUID id);

    ProductResponse create(CurrentUser user, ProductRequest request);

    ProductResponse update(CurrentUser user, UUID id, ProductRequest request);

    /** Belege kopieren die Werte, deshalb ist Loeschen immer moeglich. */
    void delete(CurrentUser user, UUID id);

    /** Artikelliste als CSV (Semikolon, UTF-8, Dezimalkomma) fuer Excel. */
    String exportCsv(CurrentUser user);
}
