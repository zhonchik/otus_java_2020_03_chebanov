package ru.otus.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ru.otus.cache.HwCache;
import ru.otus.dao.UserDao;
import ru.otus.model.User;
import ru.otus.sessionmanager.SessionManager;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import java.util.List;
import java.util.Optional;

public class DbServiceUserImpl implements DBServiceUser {
    private static final Logger logger = LoggerFactory.getLogger(DbServiceUserImpl.class);

    private final UserDao userDao;
    private final HwCache<String, User> cache;

    public DbServiceUserImpl(UserDao userDao, HwCache<String, User> cache) {
        this.userDao = userDao;
        this.cache = cache;
    }

    @Override
    public long saveUser(User user) {
        logger.info("Saving user {}", user);
        try (SessionManager sessionManager = userDao.getSessionManager()) {
            sessionManager.beginSession();
            try {
                userDao.insertOrUpdate(user);
                long id = user.getId();
                sessionManager.commitSession();
                cache.put(Long.toString(id), user);
                logger.info("User saved with id={}", id);
                return id;
            } catch (Exception e) {
                logger.error("Failed to save user", e);
                sessionManager.rollbackSession();
                throw new DbServiceException(e);
            }
        }
    }


    @Override
    public Optional<User> getUser(long id) {
        logger.info("Getting user with id={}", id);
        try (SessionManager sessionManager = userDao.getSessionManager()) {
            sessionManager.beginSession();
            try {
                var cacheUser = cache.get(Long.toString(id));
                var user = Optional.ofNullable(cacheUser).or(() -> {
                    var uo = userDao.findById(id);
                    if (uo.isPresent()) {
                        var u = uo.get();
                        cache.put(Long.toString(u.getId()), u);
                    }
                    return uo;
                });
                logger.info("Got user: {}", user);
                return user;
            } catch (Exception e) {
                logger.error("Failed to get user", e);
                sessionManager.rollbackSession();
            }
            return Optional.empty();
        }
    }

    @Override
    public List<User> getAllUsers() {
        try (SessionManager sessionManager = userDao.getSessionManager()) {
            sessionManager.beginSession();
            var hibernateSession = sessionManager.getCurrentSession().getHibernateSession();
            CriteriaBuilder builder = hibernateSession.getCriteriaBuilder();
            CriteriaQuery<User> criteria = builder.createQuery(User.class);
            criteria.from(User.class);
            return hibernateSession.createQuery(criteria).getResultList();
        }
    }
}
