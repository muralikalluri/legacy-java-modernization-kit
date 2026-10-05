package com.harborclaims.modern.web;

import com.harborclaims.modern.common.Page;
import com.harborclaims.modern.common.Paging;
import com.harborclaims.modern.customers.Customer;
import com.harborclaims.modern.customers.CustomerForm;
import com.harborclaims.modern.customers.CustomerService;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

/**
 * The migrated Customers module. URLs keep the legacy ".do" shapes. Ids are taken as Strings and parsed like legacy, so a bad
 * id is a 404 (never a 400). Redirects carry a relative Location, written directly.
 */
@Controller
public class CustomerController {

    private final CustomerService service;

    public CustomerController(CustomerService service) {
        this.service = service;
    }

    @GetMapping({"/customers/list.do", "/customers/search.do"})
    public String search(@RequestParam(required = false) String q, @RequestParam(required = false) String page, Model model) {
        String term = q == null ? "" : q.trim();
        Page<Customer> result = service.search(term, Paging.parsePage(page));
        String base = "/customers/search.do?q=" + Links.enc(term) + "&page=";
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("result", result);
        model.addAttribute("prevHref", result.hasPrev() ? base + (result.page() - 1) : null);
        model.addAttribute("nextHref", result.hasNext() ? base + (result.page() + 1) : null);
        return "customer-search";
    }

    @GetMapping("/customers/view.do")
    public String view(@RequestParam(required = false) String id, Authentication auth, Model model) {
        return renderView(NotFoundException.id(id), List.of(), auth, model);
    }

    private String renderView(int id, List<String> errors, Authentication auth, Model model) {
        Customer c = service.get(id);
        model.addAttribute("customer", c);
        model.addAttribute("policies", service.policies(id));
        model.addAttribute("errors", errors);
        model.addAttribute("canEdit", Roles.canEditCustomers(auth));
        model.addAttribute("canDelete", Roles.canDeleteCustomers(auth));
        return "customer-view";
    }

    @GetMapping("/customers/edit.do")
    public String edit(@RequestParam(required = false) String id, Model model) {
        boolean existing = id != null && !id.isBlank();
        model.addAttribute("form", existing ? CustomerForm.of(service.get(NotFoundException.id(id))) : CustomerForm.empty());
        model.addAttribute("errors", List.of());
        return "customer-edit";
    }

    @PostMapping("/customers/save.do")
    public ModelAndView save(CustomerForm form, HttpServletResponse response) {
        CustomerForm f = form.normalized();
        CustomerService.SaveResult result = service.save(f);
        if (result.ok()) {
            return redirect(response, "/customers/view.do?id=" + result.id());
        }
        ModelAndView mav = new ModelAndView("customer-edit");
        mav.addObject("form", f);
        mav.addObject("errors", result.errors());
        return mav;
    }

    @PostMapping("/customers/delete.do")
    public ModelAndView delete(@RequestParam(required = false) String id, Authentication auth, Model model, HttpServletResponse response) {
        int customerId = NotFoundException.id(id);
        String error = service.delete(customerId);
        if (error == null) {
            return redirect(response, "/customers/list.do");
        }
        String view = renderView(customerId, List.of(error), auth, model);
        return new ModelAndView(view, model.asMap());
    }

    /** A 302 with a relative Location: the browser resolves it against the origin it used (the gateway's). */
    static ModelAndView redirect(HttpServletResponse response, String location) {
        response.setStatus(HttpServletResponse.SC_FOUND);
        response.setHeader("Location", location);
        return null;
    }
}
