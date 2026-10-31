package de.rechnio.service;

import java.util.List;
import java.util.UUID;

import de.rechnio.config.CurrentUser;
import de.rechnio.dto.CustomerRequest;
import de.rechnio.dto.CustomerResponse;

public interface CustomerService {

    List<CustomerResponse> list(CurrentUser user);

    CustomerResponse get(CurrentUser user, UUID id);

    CustomerResponse create(CurrentUser user, CustomerRequest request);

    CustomerResponse update(CurrentUser user, UUID id, CustomerRequest request);
}
