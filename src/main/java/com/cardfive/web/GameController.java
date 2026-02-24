package com.cardfive.web;

import com.cardfive.ReactionType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/game")
public class GameController {
    private final WebGameService gameService;

    public GameController(WebGameService gameService) {
        this.gameService = gameService;
    }

    @PostMapping("/start")
    public WebGameService.GameStateView start() {
        try {
            gameService.startNewRound();
            return gameService.getCurrentState();
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage(), ex);
        }
    }

    @GetMapping("/state")
    public WebGameService.GameStateView state() {
        return gameService.getCurrentState();
    }

    @PostMapping("/discard")
    public WebGameService.GameStateView discard(@RequestBody DiscardRequest request) {
        try {
            gameService.submitDiscard(request.index());
            return gameService.getCurrentState();
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @PostMapping("/reaction")
    public WebGameService.GameStateView reaction(@RequestBody ReactionRequest request) {
        try {
            gameService.submitReaction(parseReaction(request.reaction()));
            return gameService.getCurrentState();
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    @PostMapping("/hu")
    public WebGameService.GameStateView hu(@RequestBody HuRequest request) {
        try {
            gameService.submitHuDecision(request.hu());
            return gameService.getCurrentState();
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    private ReactionType parseReaction(String reaction) {
        if (reaction == null || reaction.trim().isEmpty()) {
            return ReactionType.NONE;
        }
        return ReactionType.valueOf(reaction.trim().toUpperCase());
    }

    public static class DiscardRequest {
        private int index;

        public int index() {
            return index;
        }

        public void setIndex(int index) {
            this.index = index;
        }
    }

    public static class ReactionRequest {
        private String reaction;

        public String reaction() {
            return reaction;
        }

        public void setReaction(String reaction) {
            this.reaction = reaction;
        }
    }

    public static class HuRequest {
        private boolean hu;

        public boolean hu() {
            return hu;
        }

        public void setHu(boolean hu) {
            this.hu = hu;
        }
    }
}
