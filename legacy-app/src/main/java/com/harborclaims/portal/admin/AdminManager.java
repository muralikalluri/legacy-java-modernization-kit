package com.harborclaims.portal.admin;

import java.sql.SQLException;
import java.util.List;

import org.apache.log4j.Logger;

import com.harborclaims.portal.auth.AppUser;
import com.harborclaims.portal.common.BusinessException;
import com.harborclaims.portal.common.NotFoundException;
import com.harborclaims.portal.common.Page;

/**
 * User administration. There is no caller check in here on purpose: the only gate is the roles= attribute
 * on the Struts mapping, and /admin/userSave does not have one (seeded flaw: privilege escalation).
 * A changed role takes effect at that user's next login, because the session caches the user.
 */
public final class AdminManager {

    private static final Logger LOG = Logger.getLogger(AdminManager.class);
    private static final AdminManager INSTANCE = new AdminManager();

    private final AdminUserDao dao = new AdminUserDao();

    private AdminManager() {
    }

    public static AdminManager getInstance() {
        return INSTANCE;
    }

    public Page<AppUser> search(String term, int page) throws SQLException {
        return dao.search(term, page);
    }

    public AppUser get(int id) throws SQLException, NotFoundException {
        AppUser u = dao.findById(id);
        if (u == null) {
            throw new NotFoundException("User " + id);
        }
        return u;
    }

    public List<String> roleNames() throws SQLException {
        return dao.roleNames();
    }

    /** Creates (id 0) or updates a user. Returns the user id. */
    public int save(AdminUserForm f, AppUser caller) throws SQLException, BusinessException, NotFoundException {
        if (!dao.roleNames().contains(f.getRole().trim())) {
            throw new BusinessException("error.admin.role.unknown");
        }
        int id = f.idAsInt();
        if (id == 0) {
            if (dao.usernameTaken(f.getUsername().trim())) {
                throw new BusinessException("error.admin.username.taken");
            }
            int created = dao.insert(f.getUsername().trim(), f.getPassword(), f.getDisplayName().trim(),
                    f.getRole().trim(), f.isActiveFlag());
            LOG.info("User " + created + " created by " + caller.getUsername());
            return created;
        }
        get(id);
        dao.update(id, f.getPassword() == null ? "" : f.getPassword(), f.getDisplayName().trim(), f.getRole().trim(),
                f.isActiveFlag());
        LOG.info("User " + id + " updated by " + caller.getUsername());
        return id;
    }

    /** Flips the active flag. Refuses to lock the caller out or remove the last active admin. */
    public boolean toggle(int id, AppUser caller) throws SQLException, BusinessException, NotFoundException {
        AppUser target = get(id);
        boolean makeActive = !target.isActive();
        if (!makeActive) {
            if (target.getId() == caller.getId()) {
                throw new BusinessException("error.admin.toggle.self");
            }
            if ("ADMIN".equals(target.getRole()) && dao.countActiveAdmins() <= 1) {
                throw new BusinessException("error.admin.toggle.last.admin");
            }
        }
        dao.setActive(id, makeActive);
        LOG.info("User " + id + (makeActive ? " activated" : " deactivated") + " by " + caller.getUsername());
        return makeActive;
    }
}
