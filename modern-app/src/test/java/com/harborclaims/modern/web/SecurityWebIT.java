package com.harborclaims.modern.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.harborclaims.modern.PostgresIT;
import com.harborclaims.modern.security.AuthCookieCodec;
import com.harborclaims.modern.security.AuthCookieCodec.Claims;
import com.harborclaims.modern.security.AuthPolicy;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** The migrated routes through the whole filter chain: roles, CSRF, the cookie bridge, no session. Real PostgreSQL, real seed. */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityWebIT extends PostgresIT {

    private static final List<String> ALL = List.of("admin", "adjuster1", "supervisor1", "underwriter1", "csr1", "auditor1");
    private static final List<String> WRITERS = List.of("admin", "supervisor1", "csr1");
    private static final List<String> NOT_WRITERS = List.of("adjuster1", "underwriter1", "auditor1");

    @Autowired
    MockMvc mvc;

    @Autowired
    AuthCookieCodec codec;

    @Autowired
    JdbcTemplate jdbc;

    private final List<String> createdUsers = new ArrayList<>();
    private final List<Integer> createdCustomers = new ArrayList<>();
    private final List<String> revokedSids = new ArrayList<>();

    static List<String> allRoles() {
        return ALL;
    }

    static List<String> writers() {
        return WRITERS;
    }

    static List<String> notWriters() {
        return NOT_WRITERS;
    }

    @AfterEach
    void cleanUp() {
        createdUsers.forEach(u -> jdbc.update("DELETE FROM app_users WHERE username = ?", u));
        createdCustomers.forEach(id -> jdbc.update("DELETE FROM customers WHERE id = ?", id));
        revokedSids.forEach(sid -> jdbc.update("DELETE FROM auth_revocations WHERE sid = ?", sid));
    }

    // ---- helpers

    private static long now() {
        return System.currentTimeMillis();
    }

    private static String newSid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /** A cookie as legacy would issue it for a login that just happened. */
    private Cookie cookie(String user) {
        long n = now();
        return cookie(user, newSid(), n, n, n + AuthPolicy.IDLE_MILLIS);
    }

    private Cookie cookie(String user, long auth, long iat, long exp) {
        return cookie(user, newSid(), auth, iat, exp);
    }

    private Cookie cookie(String user, String sid, long auth, long iat, long exp) {
        return new Cookie(AuthCookieCodec.COOKIE, codec.sign(new Claims(user, sid, auth, iat, exp)));
    }

    private MvcResult run(MockHttpServletRequestBuilder request, Cookie cookie) throws Exception {
        MockHttpServletRequestBuilder b = request.header("Accept", "text/html");
        if (cookie != null) {
            b = b.cookie(cookie);
        }
        return mvc.perform(b).andReturn();
    }

    private MvcResult getAs(String user, String url) throws Exception {
        return run(get(url), cookie(user));
    }

    private static Document html(MvcResult r) throws Exception {
        return Jsoup.parse(r.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private static int status(MvcResult r) {
        return r.getResponse().getStatus();
    }

    /** What the page does for a browser: take the hidden token from a form this user is allowed to load. */
    private String tokenFromPage(Cookie cookie, String page) throws Exception {
        return html(run(get(page), cookie)).selectFirst("input[name=_csrf]").attr("value");
    }

    /**
     * A correctly masked token for this login, built the way Spring masks it. A role that sees no POST form has no page to copy a
     * token from, so tests build one: a 403 for such a role then proves the role check, not a missing token.
     */
    private String maskedToken(String user, String sid) {
        byte[] token = codec.csrfToken(user, sid).getBytes(StandardCharsets.UTF_8);
        byte[] random = new byte[token.length];
        new SecureRandom().nextBytes(random);
        byte[] combined = new byte[token.length * 2];
        System.arraycopy(random, 0, combined, 0, token.length);
        for (int i = 0; i < token.length; i++) {
            combined[token.length + i] = (byte) (random[i] ^ token[i]);
        }
        return Base64.getUrlEncoder().encodeToString(combined);
    }

    private MvcResult postAs(String user, String url, String token, String... fields) throws Exception {
        long auth = now();
        String sid = newSid();
        Cookie c = cookie(user, sid, auth, auth, auth + AuthPolicy.IDLE_MILLIS);
        MockHttpServletRequestBuilder b = post(url);
        for (int i = 0; i < fields.length; i += 2) {
            b = b.param(fields[i], fields[i + 1]);
        }
        if (token != null) {
            b = b.param("_csrf", token.equals("VALID") ? maskedToken(user, sid) : token);
        }
        return run(b, c);
    }

    private static String unique(String label) {
        return label + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private String newUser(String role, boolean active) {
        String name = "it-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO app_users (id, username, password, display_name, role_id, active) VALUES "
                + "((SELECT COALESCE(max(id), 0) + 1 FROM app_users), ?, 'x', 'IT user', (SELECT id FROM roles WHERE name = ?), ?)", name, role, active);
        createdUsers.add(name);
        return name;
    }

    private int newCustomer() {
        String last = unique("It");
        Integer id = jdbc.queryForObject("INSERT INTO customers (first_name, last_name, email) VALUES ('Test', ?, ?) RETURNING id", Integer.class,
                last, last + "@example.test");
        createdCustomers.add(id);
        return id;
    }

    private static void assertLoginRedirect(MvcResult r) {
        assertThat(status(r)).isEqualTo(302);
        assertThat(r.getResponse().getHeader("Location")).isEqualTo("/login.do");
    }

    // ---- anonymous callers

    @ParameterizedTest
    @ValueSource(strings = {"/customers/list.do", "/customers/search.do?q=almeida", "/customers/view.do?id=1", "/customers/edit.do",
            "/customers/edit.do?id=1", "/claims/search.do", "/customers/anything.do"})
    void anonymousRequestsAreSentToTheLoginPageWithARelativeLocation(String url) throws Exception {
        assertLoginRedirect(run(get(url), null));
    }

    @Test
    void anonymousPostsGoToTheLoginPageNotTo403() throws Exception {
        assertLoginRedirect(run(post("/customers/save.do").param("firstName", "x").param("lastName", "y").param("email", "z@z.zz"), null));
        assertLoginRedirect(run(post("/customers/delete.do").param("id", "1"), null));
    }

    @Test
    void theHealthEndpointIsOpen() throws Exception {
        MvcResult r = mvc.perform(get("/actuator/health").header("Accept", "application/json")).andReturn();
        assertThat(status(r)).isEqualTo(200);
        assertThat(r.getResponse().getContentAsString()).contains("UP");
    }

    // ---- reading

    @ParameterizedTest
    @MethodSource("allRoles")
    void everySignedInRoleCanReadCustomersAndSearchClaims(String user) throws Exception {
        Document list = html(getAs(user, "/customers/list.do"));
        assertThat(list.select("#results [data-customer-id]")).hasSize(10);
        Document search = html(getAs(user, "/customers/search.do?q=almeida"));
        assertThat(search.select("#results [data-customer-id]").eachAttr("data-customer-id")).containsExactly("1", "21", "11");
        assertThat(search.selectFirst("#total").text()).isEqualTo("3");
        assertThat(html(getAs(user, "/customers/view.do?id=1")).selectFirst("#customer-name").text()).isEqualTo("Amara Almeida");
        assertThat(html(getAs(user, "/claims/search.do")).select("#results [data-claim-id]")).hasSize(10);
    }

    // ---- roles, CSRF and the order of the checks

    @ParameterizedTest
    @MethodSource("writers")
    void writersCanOpenTheFormAndSaveWithAToken(String user) throws Exception {
        assertThat(status(getAs(user, "/customers/edit.do"))).isEqualTo(200);
        assertThat(status(getAs(user, "/customers/edit.do?id=1"))).isEqualTo(200);
        String last = unique("Save");
        MvcResult r = postAs(user, "/customers/save.do", "VALID", "id", "", "firstName", "Test", "lastName", last, "email", last + "@example.test");
        assertThat(status(r)).isEqualTo(302);
        String location = r.getResponse().getHeader("Location");
        assertThat(location).matches("/customers/view\\.do\\?id=\\d+");
        createdCustomers.add(Integer.parseInt(location.substring(location.indexOf('=') + 1)));
    }

    @ParameterizedTest
    @MethodSource("notWriters")
    void otherRolesAreForbiddenTheFormAndTheSaveEvenWithAValidToken(String user) throws Exception {
        assertThat(status(getAs(user, "/customers/edit.do"))).isEqualTo(403);
        assertThat(status(getAs(user, "/customers/edit.do?id=1"))).isEqualTo(403);
        String last = unique("Deny");
        MvcResult r = postAs(user, "/customers/save.do", "VALID", "id", "", "firstName", "Test", "lastName", last, "email", last + "@example.test");
        assertThat(status(r)).isEqualTo(403);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customers WHERE last_name = ?", Integer.class, last)).isZero();
    }

    @Test
    void onlyAnAdministratorMayDelete() throws Exception {
        for (String user : List.of("csr1", "supervisor1", "adjuster1", "underwriter1", "auditor1")) {
            int id = newCustomer();
            assertThat(status(postAs(user, "/customers/delete.do", "VALID", "id", String.valueOf(id)))).as(user).isEqualTo(403);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM customers WHERE id = ?", Integer.class, id)).isEqualTo(1);
        }
        int id = newCustomer();
        MvcResult r = postAs("admin", "/customers/delete.do", "VALID", "id", String.valueOf(id));
        assertThat(status(r)).isEqualTo(302);
        assertThat(r.getResponse().getHeader("Location")).isEqualTo("/customers/list.do");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customers WHERE id = ?", Integer.class, id)).isZero();
    }

    @Test
    void theRoleCheckComesBeforeTheExistenceCheckAndBeforeValidation() throws Exception {
        assertThat(status(getAs("auditor1", "/customers/edit.do?id=99999"))).isEqualTo(403);
        assertThat(status(postAs("csr1", "/customers/delete.do", "VALID", "id", "99999"))).isEqualTo(403);
        assertThat(status(postAs("auditor1", "/customers/save.do", "VALID", "firstName", "", "lastName", "", "email", "bad"))).isEqualTo(403);
        assertThat(status(postAs("admin", "/customers/delete.do", "VALID", "id", "99999"))).isEqualTo(404);
    }

    @Test
    void aPostWithoutAValidTokenIsRefusedForASignedInUser() throws Exception {
        String last = unique("NoTok");
        long auth = now();
        String sid = newSid();
        Cookie c = cookie("csr1", sid, auth, auth, auth + AuthPolicy.IDLE_MILLIS);
        MockHttpServletRequestBuilder base = post("/customers/save.do").param("firstName", "Test").param("lastName", last).param("email", last + "@example.test");
        assertThat(status(run(base, c))).as("no token").isEqualTo(403);
        assertThat(status(run(post("/customers/save.do").param("firstName", "Test").param("lastName", last).param("email", last + "@example.test")
                .param("_csrf", "not-a-token"), c))).as("garbage token").isEqualTo(403);
        assertThat(status(run(post("/customers/save.do").param("firstName", "Test").param("lastName", last).param("email", last + "@example.test")
                .param("_csrf", maskedToken("admin", sid)), c))).as("another user's token").isEqualTo(403);
        assertThat(status(run(post("/customers/save.do").param("firstName", "Test").param("lastName", last).param("email", last + "@example.test")
                .param("_csrf", maskedToken("csr1", newSid())), c))).as("a token for another login").isEqualTo(403);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customers WHERE last_name = ?", Integer.class, last)).isZero();
    }

    @Test
    void theTokenOnAPageWorksForThePostAndSurvivesACookieRefresh() throws Exception {
        long auth = now() - 20 * 60_000;
        String sid = newSid();
        Cookie old = cookie("csr1", sid, auth, auth, now() + 5 * 60_000);
        String token = tokenFromPage(old, "/customers/edit.do");
        Cookie refreshed = cookie("csr1", sid, auth, now(), now() + AuthPolicy.IDLE_MILLIS);
        String last = unique("Tok");
        MvcResult r = run(post("/customers/save.do").param("firstName", "Test").param("lastName", last).param("email", last + "@example.test").param("_csrf", token), refreshed);
        assertThat(status(r)).as("a form opened before the cookie was refreshed still posts").isEqualTo(302);
        String location = r.getResponse().getHeader("Location");
        createdCustomers.add(Integer.parseInt(location.substring(location.indexOf('=') + 1)));
    }

    @Test
    void deleteIsPostOnlyAGetIsRefused() throws Exception {
        assertThat(status(getAs("admin", "/customers/delete.do?id=1"))).isEqualTo(405);
        assertThat(status(getAs("csr1", "/customers/delete.do?id=1"))).isEqualTo(403);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customers WHERE id = 1", Integer.class)).isEqualTo(1);
    }

    @Test
    void deletingACustomerWithPoliciesShowsTheErrorOnTheCustomerPage() throws Exception {
        MvcResult r = postAs("admin", "/customers/delete.do", "VALID", "id", "1");
        assertThat(status(r)).isEqualTo(200);
        Document d = html(r);
        assertThat(d.select("#errors li").eachText()).containsExactly("Customer has policies and cannot be deleted");
        assertThat(d.selectFirst("#customer-name").text()).isEqualTo("Amara Almeida");
    }

    @Test
    void aValidationFailureShowsTheFormAgainWithTheMessagesAndTheEnteredValues() throws Exception {
        MvcResult r = postAs("csr1", "/customers/save.do", "VALID", "id", "", "firstName", "", "lastName", "Smith", "email", "bad");
        assertThat(status(r)).isEqualTo(200);
        Document d = html(r);
        assertThat(d.select("#errors li").eachText()).containsExactlyInAnyOrder("firstName is required", "Email address is not valid");
        assertThat(d.selectFirst("input[name=lastName]").attr("value")).isEqualTo("Smith");
        assertThat(d.selectFirst("input[name=email]").attr("value")).isEqualTo("bad");
        assertThat(d.selectFirst("form[action='/customers/save.do']")).isNotNull();
    }

    // ---- ids parse like legacy: a bad one is 404, never 400

    @ParameterizedTest
    @ValueSource(strings = {"99999", "abc", "-1", "99999999999", "1.5", "", "0x1", "#1", "1 2"})
    void anUnknownOrMalformedIdIsNotFound(String id) throws Exception {
        assertThat(status(run(get("/customers/view.do").param("id", id), cookie("csr1")))).as("view id=" + id).isEqualTo(404);
        assertThat(status(run(get("/customers/edit.do").param("id", id.isEmpty() ? "x" : id), cookie("csr1")))).as("edit id=" + id).isEqualTo(404);
    }

    @ParameterizedTest
    @ValueSource(strings = {"+1", " 1 ", "01"})
    void aSignedOrPaddedIdIsAccepted(String id) throws Exception {
        assertThat(status(run(get("/customers/view.do").param("id", id), cookie("csr1")))).as("id=" + id).isEqualTo(200);
    }

    @Test
    void aMissingIdIsNotFoundAndEditWithoutOneIsTheNewForm() throws Exception {
        assertThat(status(getAs("csr1", "/customers/view.do"))).isEqualTo(404);
        MvcResult r = getAs("csr1", "/customers/edit.do");
        assertThat(status(r)).isEqualTo(200);
        assertThat(html(r).selectFirst("input[name=id]").attr("value")).isEmpty();
    }

    @Test
    void anUnknownPathIsNotFoundForASignedInUser() throws Exception {
        assertThat(status(getAs("csr1", "/customers/nothing-here.do"))).isEqualTo(404);
        assertThat(status(getAs("csr1", "/customers/edit.do/"))).isEqualTo(404);
    }

    // ---- the cookie

    @Test
    void aBadCookieIsTreatedAsNoCookie() throws Exception {
        long n = now();
        String good = codec.sign(new Claims("csr1", newSid(), n, n, n + AuthPolicy.IDLE_MILLIS));
        String[] parts = good.split("\\.");
        List<Cookie> bad = List.of(
                new Cookie(AuthCookieCodec.COOKIE, parts[0] + "." + parts[1] + "." + parts[2].substring(1) + "A"),
                new Cookie(AuthCookieCodec.COOKIE, "garbage"),
                new Cookie(AuthCookieCodec.COOKIE, ""),
                new Cookie(AuthCookieCodec.COOKIE, good + ".x"),
                new Cookie(AuthCookieCodec.COOKIE, "v2." + parts[1] + "." + parts[2]),
                new Cookie(AuthCookieCodec.COOKIE, AuthCookieCodec.of("another-secret-0123456789-abcdefghijkl", "", false)
                        .sign(new Claims("admin", newSid(), n, n, n + AuthPolicy.IDLE_MILLIS))),
                cookie("csr1", n - 60_000, n - 60_000, n - 1),
                cookie("csr1", n + 10 * 60_000, n + 10 * 60_000, n + 40 * 60_000),
                cookie("csr1", n - AuthPolicy.CAP_MILLIS - 1000, n, n + 10 * 60_000),
                cookie("nobody-" + UUID.randomUUID().toString().substring(0, 6), n, n, n + AuthPolicy.IDLE_MILLIS));
        for (Cookie c : bad) {
            assertLoginRedirect(run(get("/customers/list.do"), c));
        }
    }

    @Test
    void anInactiveUserIsRefusedAtOnce() throws Exception {
        String user = newUser("CSR", true);
        assertThat(status(getAs(user, "/customers/list.do"))).isEqualTo(200);
        jdbc.update("UPDATE app_users SET active = false WHERE username = ?", user);
        assertLoginRedirect(getAs(user, "/customers/list.do"));
    }

    @Test
    void aLogoutRevokesThatLoginOnlyNotTheUsersOtherLogins() throws Exception {
        String user = newUser("CSR", true);
        long n = now();
        String sidA = newSid();
        String sidB = newSid();
        Cookie loginA = cookie(user, sidA, n - 5000, n - 5000, n - 5000 + AuthPolicy.IDLE_MILLIS);
        Cookie loginB = cookie(user, sidB, n - 4000, n - 4000, n - 4000 + AuthPolicy.IDLE_MILLIS);
        assertThat(status(run(get("/customers/list.do"), loginA))).isEqualTo(200);
        assertThat(status(run(get("/customers/list.do"), loginB))).isEqualTo(200);

        jdbc.update("INSERT INTO auth_revocations (sid, revoked_at_ms) VALUES (?, ?)", sidA, n);
        revokedSids.add(sidA);

        assertLoginRedirect(run(get("/customers/list.do"), loginA));
        Cookie refreshedA = cookie(user, sidA, n - 5000, now(), now() + AuthPolicy.IDLE_MILLIS);
        assertLoginRedirect(run(get("/customers/list.do"), refreshedA));
        assertThat(status(run(get("/customers/list.do"), loginB))).as("the other login of the same user is untouched").isEqualTo(200);
    }

    @Test
    void theRoleComesFromTheDatabaseOnEveryRequest() throws Exception {
        String user = newUser("CSR", true);
        assertThat(status(getAs(user, "/customers/edit.do"))).isEqualTo(200);
        jdbc.update("UPDATE app_users SET role_id = (SELECT id FROM roles WHERE name = 'AUDITOR') WHERE username = ?", user);
        assertThat(status(getAs(user, "/customers/edit.do"))).as("demoted: the same cookie, now forbidden").isEqualTo(403);
        jdbc.update("UPDATE app_users SET role_id = (SELECT id FROM roles WHERE name = 'ADMIN') WHERE username = ?", user);
        assertThat(status(postAs(user, "/customers/delete.do", "VALID", "id", "99999"))).as("promoted: now an administrator").isEqualTo(404);
    }

    // ---- no session, and the refresh

    @Test
    void noResponseCreatesAnHttpSessionOrSetsASessionCookie() throws Exception {
        List<MvcResult> results = List.of(getAs("csr1", "/customers/list.do"), getAs("admin", "/customers/view.do?id=1"),
                getAs("csr1", "/customers/edit.do"), run(get("/customers/list.do"), null),
                postAs("csr1", "/customers/save.do", "VALID", "firstName", "", "lastName", "", "email", ""));
        for (MvcResult r : results) {
            assertThat(r.getRequest().getSession(false)).isNull();
            MockHttpServletResponse res = r.getResponse();
            assertThat(res.getHeaders("Set-Cookie")).noneMatch(h -> h.toUpperCase().contains("SESSIONID") || h.toUpperCase().contains("MODERN_SID"));
        }
    }

    @Test
    void aCookieInTheSecondHalfOfItsWindowIsRefreshedKeepingTheLoginTime() throws Exception {
        long auth = now() - 20 * 60_000;
        MvcResult r = run(get("/customers/list.do"), cookie("csr1", auth, auth, now() + 5 * 60_000));
        assertThat(status(r)).isEqualTo(200);
        List<String> headers = r.getResponse().getHeaders("Set-Cookie");
        assertThat(headers).hasSize(1);
        String header = headers.get(0);
        assertThat(header).startsWith("HARBOR_AUTH=").contains("Path=/", "HttpOnly", "SameSite=Lax", "Max-Age=").doesNotContain("Domain", "Secure");
        String token = header.substring("HARBOR_AUTH=".length(), header.indexOf(';'));
        Claims fresh = codec.verify(token).orElseThrow();
        assertThat(fresh.authMillis()).as("a refresh must not move the login time").isEqualTo(auth);
        assertThat(fresh.sid()).as("or change which login it is").isNotBlank();
        assertThat(fresh.expMillis()).isGreaterThan(now() + 25 * 60_000);
    }

    @Test
    void aFreshCookieIsLeftAlone() throws Exception {
        MvcResult r = getAs("csr1", "/customers/list.do");
        assertThat(r.getResponse().getHeaders("Set-Cookie")).isEmpty();
    }

    @Test
    void aRefreshNeverExtendsPastTheLifetimeCap() throws Exception {
        long auth = now() - AuthPolicy.CAP_MILLIS + 5 * 60_000;
        MvcResult r = run(get("/customers/list.do"), cookie("csr1", auth, auth, now() + 5 * 60_000));
        assertThat(status(r)).isEqualTo(200);
        String header = r.getResponse().getHeader("Set-Cookie");
        Claims fresh = codec.verify(header.substring("HARBOR_AUTH=".length(), header.indexOf(';'))).orElseThrow();
        assertThat(fresh.expMillis()).isLessThanOrEqualTo(auth + AuthPolicy.CAP_MILLIS);
    }

    // ---- the contract hooks the characterisation suite relies on

    @Test
    void editAndDeleteControlsAppearOnlyForTheRolesThatMayUseThem() throws Exception {
        for (String user : ALL) {
            Document d = html(getAs(user, "/customers/view.do?id=1"));
            assertThat(d.selectFirst("#edit-link") != null).as(user + " edit").isEqualTo(WRITERS.contains(user));
            assertThat(d.selectFirst("#delete-link") != null).as(user + " delete").isEqualTo(user.equals("admin"));
        }
        Document admin = html(getAs("admin", "/customers/view.do?id=1"));
        assertThat(admin.selectFirst("#edit-link").attr("href")).isEqualTo("/customers/edit.do?id=1");
        var delete = admin.selectFirst("#delete-link");
        assertThat(delete.tagName()).isEqualTo("form");
        assertThat(delete.attr("method")).isEqualTo("post");
        assertThat(delete.attr("action")).isEqualTo("/customers/delete.do");
        assertThat(delete.selectFirst("input[name=id]").attr("value")).isEqualTo("1");
        assertThat(delete.selectFirst("input[name=_csrf]").attr("value")).isNotEmpty();
    }

    @Test
    void theCustomerPageCarriesTheDocumentedHooks() throws Exception {
        Document d = html(getAs("csr1", "/customers/view.do?id=1"));
        assertThat(d.selectFirst("#customer-details").attr("data-customer-id")).isEqualTo("1");
        assertThat(d.selectFirst("#customer-details").text()).contains("amara.almeida@example.test", "555-0101", "1961-02-01", "107 Harbor Lane");
        assertThat(d.select("#policies tr").stream().skip(1).map(tr -> tr.selectFirst("td").text()).toList()).containsExactly("POL-000001", "POL-000031");
        assertThat(d.selectFirst("#policies").text()).contains("50,000.00");
    }

    @Test
    void theClaimsSearchPageCarriesTheDocumentedHooks() throws Exception {
        Document d = html(getAs("auditor1", "/claims/search.do?q=CLM-000007&status=UNDER_REVIEW"));
        assertThat(d.select("#results [data-claim-id]").eachAttr("data-claim-id")).containsExactly("7");
        assertThat(d.selectFirst("#results [data-claim-id=7]").attr("data-status")).isEqualTo("UNDER_REVIEW");
        assertThat(d.selectFirst("#results [data-claim-id=7] a").attr("href")).isEqualTo("/claim/view.do?id=7");
        assertThat(d.selectFirst("form[action$='/claims/search.do'] input[name=q]").attr("value")).isEqualTo("CLM-000007");
        assertThat(d.selectFirst("form[action$='/claims/search.do'] select[name=status] option[selected]").attr("value")).isEqualTo("UNDER_REVIEW");
        assertThat(d.selectFirst("#total").text()).isEqualTo("1");
    }

    @Test
    void pagerLinksCarryTheTermAndTheStatus() throws Exception {
        Document first = html(run(get("/customers/search.do").param("q", "a%"), cookie("csr1")));
        assertThat(first.selectFirst("#pager #prev")).isNull();
        assertThat(first.selectFirst("#pager #next").attr("href")).isEqualTo("/customers/search.do?q=a%25&page=2");
        Document claims = html(run(get("/claims/search.do").param("q", "%").param("status", "").param("page", "2"), cookie("csr1")));
        assertThat(claims.selectFirst("#pager #prev").attr("href")).isEqualTo("/claims/search.do?q=%25&status=&page=1");
    }

    @Test
    void htmlIsServedAsUtf8AndEscapesWhatItShows() throws Exception {
        int id = newCustomer();
        jdbc.update("UPDATE customers SET first_name = ?, last_name = ? WHERE id = ?", "Zoë", "<b>x</b>&\"", id);
        MvcResult r = getAs("csr1", "/customers/view.do?id=" + id);
        assertThat(r.getResponse().getContentType()).containsIgnoringCase("charset=UTF-8");
        Document d = html(r);
        assertThat(d.selectFirst("#customer-name b")).as("escaped, not markup").isNull();
        assertThat(d.selectFirst("#customer-name").text()).isEqualTo("Zoë <b>x</b>&\"");
    }
}
