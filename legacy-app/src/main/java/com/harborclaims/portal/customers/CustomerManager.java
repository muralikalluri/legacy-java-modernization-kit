package com.harborclaims.portal.customers;

import java.sql.SQLException;
import java.util.List;

import com.harborclaims.portal.common.BusinessException;
import com.harborclaims.portal.common.NotFoundException;
import com.harborclaims.portal.common.Page;
import com.harborclaims.portal.policies.Policy;
import com.harborclaims.portal.policies.PolicyDao;

public final class CustomerManager {

    private static final CustomerManager INSTANCE = new CustomerManager();

    private final CustomerDao dao = new CustomerDao();
    private final PolicyDao policyDao = new PolicyDao();

    private CustomerManager() {
    }

    public static CustomerManager getInstance() {
        return INSTANCE;
    }

    public Page<Customer> search(String term, int page) throws SQLException {
        return dao.search(term, page);
    }

    public Customer get(int id) throws SQLException, NotFoundException {
        Customer c = dao.findById(id);
        if (c == null) {
            throw new NotFoundException("Customer " + id);
        }
        return c;
    }

    public List<Policy> policiesOf(int customerId) throws SQLException {
        return policyDao.listByCustomer(customerId);
    }

    /** Returns the saved customer id. */
    public int save(Customer c) throws SQLException, BusinessException, NotFoundException {
        if (dao.emailInUse(c.getEmail(), c.getId())) {
            throw new BusinessException("error.customer.email.duplicate");
        }
        if (c.getId() == 0) {
            return dao.insert(c);
        }
        get(c.getId());
        dao.update(c);
        return c.getId();
    }

    public void delete(int id) throws SQLException, BusinessException, NotFoundException {
        get(id);
        if (policyDao.countByCustomer(id) > 0) {
            throw new BusinessException("error.customer.has.policies");
        }
        dao.delete(id);
    }
}
