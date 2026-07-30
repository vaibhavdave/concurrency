package com.concurrency.lab.m14_virtual_threads_structured_concurrency;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/virtual-threads")
public class VirtualThreadDemoController {

    @GetMapping("/whoami")
    public ThreadReport whoami() {
        Thread current = Thread.currentThread();
        return new ThreadReport(current.getName(), current.isVirtual());
    }

    public record ThreadReport(String threadName, boolean virtual) {
    }
}
