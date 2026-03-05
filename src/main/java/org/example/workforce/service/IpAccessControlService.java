package org.example.workforce.service;

import org.example.workforce.dto.IpRangeRequest;
import org.example.workforce.exception.BadRequestException;
import org.example.workforce.exception.DuplicateResourceException;
import org.example.workforce.exception.ResourceNotFoundException;
import org.example.workforce.model.AllowedIpRange;
import org.example.workforce.repository.AllowedIpRangeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.example.workforce.util.NetworkIpUtil;

import java.net.InetAddress;
import java.util.List;

@Service
public class IpAccessControlService {

    @Autowired
    private AllowedIpRangeRepository ipRangeRepository;

    @Transactional(readOnly = true)
    public List<AllowedIpRange> getAllIpRanges() {
        return ipRangeRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<AllowedIpRange> getActiveIpRanges() {
        return ipRangeRepository.findByIsActiveTrue();
    }

    @Transactional
    public AllowedIpRange addIpRange(IpRangeRequest request) {

        validateIpOrCidr(request.getIpRange());

        if (ipRangeRepository.existsByIpRange(request.getIpRange().trim())) {
            throw new DuplicateResourceException("IP range '" + request.getIpRange() + "' is already whitelisted");
        }

        AllowedIpRange ipRange = AllowedIpRange.builder()
                .ipRange(request.getIpRange().trim())
                .description(request.getDescription().trim())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .build();

        return ipRangeRepository.save(ipRange);
    }

    @Transactional
    public AllowedIpRange updateIpRange(Integer id, IpRangeRequest request) {
        AllowedIpRange existing = ipRangeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("IP range not found with id: " + id));

        if (request.getIpRange() != null && !request.getIpRange().isBlank()) {
            validateIpOrCidr(request.getIpRange());
            existing.setIpRange(request.getIpRange().trim());
        }
        if (request.getDescription() != null && !request.getDescription().isBlank()) {
            existing.setDescription(request.getDescription().trim());
        }
        if (request.getIsActive() != null) {
            existing.setIsActive(request.getIsActive());
        }

        return ipRangeRepository.save(existing);
    }

    @Transactional
    public AllowedIpRange toggleIpRange(Integer id) {
        AllowedIpRange existing = ipRangeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("IP range not found with id: " + id));
        existing.setIsActive(!existing.getIsActive());
        return ipRangeRepository.save(existing);
    }

    @Transactional
    public void deleteIpRange(Integer id) {
        if (!ipRangeRepository.existsById(id)) {
            throw new ResourceNotFoundException("IP range not found with id: " + id);
        }
        ipRangeRepository.deleteById(id);
    }

    public boolean isIpAllowed(String clientIp) {
        List<AllowedIpRange> activeRanges = ipRangeRepository.findByIsActiveTrue();

        if (activeRanges.isEmpty()) {
            return true;
        }

        String normalizedIp = normalizeIp(clientIp);

        for (AllowedIpRange range : activeRanges) {
            if (isIpInRange(normalizedIp, range.getIpRange().trim())) {
                return true;
            }
        }
        return false;
    }

    private boolean isIpInRange(String clientIp, String ipRange) {
        try {
            if (ipRange.contains("/")) {

                String[] parts = ipRange.split("/");
                String networkAddress = parts[0];
                int prefixLength = Integer.parseInt(parts[1]);

                InetAddress clientAddr = InetAddress.getByName(clientIp);
                InetAddress networkAddr = InetAddress.getByName(networkAddress);

                byte[] clientBytes = clientAddr.getAddress();
                byte[] networkBytes = networkAddr.getAddress();

                if (clientBytes.length != networkBytes.length) {
                    return false;
                }

                int fullBytes = prefixLength / 8;
                int remainingBits = prefixLength % 8;

                for (int i = 0; i < fullBytes; i++) {
                    if (clientBytes[i] != networkBytes[i]) return false;
                }

                if (remainingBits > 0 && fullBytes < clientBytes.length) {
                    int mask = 0xFF << (8 - remainingBits);
                    if ((clientBytes[fullBytes] & mask) != (networkBytes[fullBytes] & mask)) {
                        return false;
                    }
                }
                return true;
            } else {

                String normalizedRange = normalizeIp(ipRange);
                return clientIp.equals(normalizedRange);
            }
        } catch (Exception e) {
            return false;
        }
    }

    private String normalizeIp(String ip) {
        if (ip == null) return "";
        if (NetworkIpUtil.isLoopback(ip)) {
            String networkIp = NetworkIpUtil.getLocalNetworkIp();
            return networkIp != null ? networkIp : ip;
        }
        return ip;
    }

    private void validateIpOrCidr(String input) {
        if (input == null || input.isBlank()) {
            throw new BadRequestException("IP range cannot be empty");
        }

        String trimmed = input.trim();

        try {
            if (trimmed.contains("/")) {
                String[] parts = trimmed.split("/");
                if (parts.length != 2) {
                    throw new BadRequestException("Invalid CIDR format: " + trimmed);
                }
                InetAddress.getByName(parts[0]);
                int prefix = Integer.parseInt(parts[1]);
                InetAddress addr = InetAddress.getByName(parts[0]);
                int maxPrefix = addr.getAddress().length == 4 ? 32 : 128;
                if (prefix < 0 || prefix > maxPrefix) {
                    throw new BadRequestException("CIDR prefix must be between 0 and " + maxPrefix);
                }
            } else {
                InetAddress.getByName(trimmed);
            }
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("Invalid IP address or CIDR range: " + trimmed);
        }
    }
}
