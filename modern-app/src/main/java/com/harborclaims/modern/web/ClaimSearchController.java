package com.harborclaims.modern.web;

import com.harborclaims.modern.claims.ClaimRow;
import com.harborclaims.modern.claims.ClaimSearchRepository;
import com.harborclaims.modern.common.Page;
import com.harborclaims.modern.common.Paging;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** The migrated claims search. Claim pages and claim changes stay in the legacy app. */
@Controller
public class ClaimSearchController {

    private final ClaimSearchRepository repository;

    public ClaimSearchController(ClaimSearchRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/claims/search.do")
    public String search(@RequestParam(required = false) String q, @RequestParam(required = false) String status,
            @RequestParam(required = false) String page, Model model) {
        String term = q == null ? "" : q.trim();
        String cleanStatus = status == null ? "" : status.trim();
        Page<ClaimRow> result = repository.search(term, cleanStatus, Paging.parsePage(page));
        String base = "/claims/search.do?q=" + Links.enc(term) + "&status=" + Links.enc(cleanStatus) + "&page=";
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("status", status == null ? "" : status);
        model.addAttribute("statuses", ClaimSearchRepository.STATUSES);
        model.addAttribute("result", result);
        model.addAttribute("prevHref", result.hasPrev() ? base + (result.page() - 1) : null);
        model.addAttribute("nextHref", result.hasNext() ? base + (result.page() + 1) : null);
        return "claim-search";
    }
}
