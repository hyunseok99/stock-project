package org.team4.sol_server.domain.user;

import org.team4.sol_server.domain.user.dto.UserInitRequestDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.team4.sol_server.error.CustomException;


@RestController
@RequestMapping("api/users")
@RequiredArgsConstructor
@Log4j2
public class UserServiceController {
    private final UserService userService;

    @PostMapping("/init")
    public ResponseEntity<String> initGuestUser(@RequestBody UserInitRequestDTO requestDTO){
        if(requestDTO.getUserId() == null || requestDTO.getUserId().isBlank()){
            throw new CustomException(UserErrorCode.INVALID_USER_ID);
        }

        userService.createGuestIfNotExist(requestDTO.getUserId());
        return ResponseEntity.ok("Guest user initialized successfully");

    }
}
