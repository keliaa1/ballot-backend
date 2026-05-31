package com.ballot.dto.chat;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SendChatMessageRequest {

    @NotBlank(message = "Content cannot be blank")
    private String content;
}
