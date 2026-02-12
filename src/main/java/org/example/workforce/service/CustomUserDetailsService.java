package org.example.workforce.service;
import org.example.workforce.model.Employee;
import org.example.workforce.repository.EmployeeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import java.util.Collections;

@Service
public class CustomUserDetailsService implements UserDetailsService {
    @Autowired
    private EmployeeRepository employeeRepository;
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException{
        Employee employee = employeeRepository.findByEmail(email).orElseThrow(()-> new UsernameNotFoundException("Employee not found with email: "+ email));
        if(!employee.getIsActive()){
            throw new DisabledException("Account is deactivated. Contact Admin");
        }
        return new User(employee.getEmail(), employee.getPasswordHash(), Collections.singleton(new SimpleGrantedAuthority("ROLE_" + employee.getRole().name())));
    }
}
