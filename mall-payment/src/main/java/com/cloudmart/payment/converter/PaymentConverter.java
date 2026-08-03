package com.cloudmart.payment.converter;

import com.cloudmart.payment.dto.PaymentDTO;
import com.cloudmart.payment.entity.Payment;
import com.cloudmart.payment.vo.PaymentVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PaymentConverter {

    @Mapping(target = "payUrl", ignore = true)
    PaymentDTO toDTO(Payment payment);

    PaymentVO toVO(Payment payment);

    PaymentVO dtoToVO(PaymentDTO dto);
}
